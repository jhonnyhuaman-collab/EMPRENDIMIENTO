import { randomBytes, randomUUID, createHash, createHmac, timingSafeEqual } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { settingsSchema, courseSchema, evaluationSchema, classSchema, initialDocument, limaNow, DAY_NAMES, daysBetween, addDays, estimatedHours } from './domain.mjs';
import { generatePlan, calendarIcs } from './planner.mjs';
import { analyze, importSchema, providerInfo } from './ai.mjs';
import { alignSyllabusDates } from './review.mjs';

const authSchema = z.object({ email: z.string().trim().toLowerCase().email().max(254), password: z.string().min(8).max(72).refine(s => Buffer.byteLength(s) <= 72, 'La contraseña es demasiado larga.') });
const versionSchema = z.object({ version: z.number().int().positive() });
const dummyHash = bcrypt.hashSync('no-es-una-clave-de-cuenta', 12);
const hash = text => createHash('sha256').update(text).digest('hex');
const fail = (status, message) => { throw Object.assign(new Error(message), { status }); };
const safeEqual = (a, b) => typeof a === 'string' && /^[a-f0-9]{64}$/.test(a) && timingSafeEqual(Buffer.from(a), Buffer.from(b));
const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY' };
const json = (body, status = 200, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...headers, ...extra } });

export function createHandler({ db, env, now = () => new Date(), fetchAI = fetch }) {
  const production = env.NETLIFY === 'true' || env.NODE_ENV === 'production';
  const cookieName = production ? '__Host-pa_session' : 'pa_session';
  const secret = env.SESSION_SECRET;
  const csrf = token => createHmac('sha256', secret).update('csrf:' + token).digest('hex');
  const sessionCookie = (token, age = 604800) => `${cookieName}=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${age}${production ? '; Secure' : ''}`;

  async function limit(key, count, seconds) {
    const identity = createHmac('sha256', secret).update(key).digest('hex');
    const expires = new Date(now().valueOf() + seconds * 1000);
    const { rows } = await db.query(`INSERT INTO pa_netlify_limits(key,hits,expires_at) VALUES($1,1,$2)
      ON CONFLICT(key) DO UPDATE SET
        hits=CASE WHEN pa_netlify_limits.expires_at < $3 THEN 1 ELSE pa_netlify_limits.hits+1 END,
        expires_at=CASE WHEN pa_netlify_limits.expires_at < $3 THEN $2 ELSE pa_netlify_limits.expires_at END
      RETURNING hits`, [identity, expires, now()]);
    if (rows[0].hits > count) fail(429, 'Demasiados intentos. Espera un momento antes de volver a probar.');
  }
  async function body(request) {
    if (!request.headers.get('content-type')?.startsWith('application/json')) fail(415, 'Usa contenido JSON.');
    if (Number(request.headers.get('content-length')) > 4300000) fail(413, 'El archivo o contenido es demasiado grande.');
    const raw = await request.text();
    if (Buffer.byteLength(raw) > 4300000) fail(413, 'El contenido supera el limite permitido.');
    try { return JSON.parse(raw); } catch { fail(400, 'El contenido no es JSON valido.'); }
  }
  async function authenticate(request) {
    const cookie = request.headers.get('cookie')?.split(';').map(s => s.trim()).find(s => s.startsWith(cookieName + '='));
    const token = cookie?.slice(cookieName.length + 1);
    if (!token || !/^[a-f0-9]{64}$/.test(token)) fail(401, 'Inicia sesion para continuar.');
    const { rows } = await db.query(`SELECT u.id,u.email,s.document,s.version FROM pa_netlify_sessions se
      JOIN pa_netlify_users u ON u.id=se.user_id JOIN pa_netlify_state s ON s.user_id=u.id
      WHERE se.token_hash=$1 AND se.expires_at>$2`, [hash(token), now()]);
    if (!rows.length) fail(401, 'Tu sesion termino. Vuelve a ingresar.');
    return { ...rows[0], token };
  }
  const packet = user => ({ user: { id: user.id, email: user.email }, state: user.document, version: user.version,
    csrfToken: csrf(user.token), today: limaNow(now()).date, provider: providerInfo(env) });
  async function save(user, document, version) {
    if (document.courses.length > 100 || document.evaluations.length > 1000 || document.classes.length > 500 || document.blocks.length > 20000) fail(400, 'Alcanzaste el limite de registros de esta cuenta.');
    const serialized = JSON.stringify(document);
    if (Buffer.byteLength(serialized) > 2000000) fail(400, 'La cuenta contiene demasiada informacion.');
    const { rows } = await db.query(`UPDATE pa_netlify_state SET document=$1::jsonb,version=version+1,updated_at=$2
      WHERE user_id=$3 AND version=$4 RETURNING version`, [serialized, now(), user.id, version]);
    if (!rows.length) fail(409, 'Tus datos cambiaron en otra ventana. Actualiza antes de volver a guardar.');
    user.document = document; user.version = rows[0].version;
    return packet(user);
  }
  const requireCourse = (doc, id) => doc.courses.find(c => c.id === id) || fail(404, 'No se encontro el curso de tu cuenta.');
  function validateWeight(doc, evaluation, ignoreId) {
    const total = doc.evaluations.filter(e => e.courseId === evaluation.courseId && e.id !== ignoreId).reduce((sum, e) => sum + e.weight, 0) + evaluation.weight;
    if (total > 100.001) fail(400, 'Los pesos de las evaluaciones de este curso superan el 100%.');
  }
  // Con `notes`, el cruce se avisa y la clase se guarda igual (como Render al importar horario o silabo:
  // un consolidado real puede tener cruces). Sin `notes` (clase agregada a mano) se rechaza.
  function validateClass(doc, cl, ignoreId, notes) {
    const conflict = doc.classes.find(c => c.id !== ignoreId && c.weekday === cl.weekday && c.start < cl.end && cl.start < c.end && (c.frequency === 'SEMANAL' || cl.frequency === 'SEMANAL' || c.frequency === cl.frequency));
    if (!conflict) return;
    if (!notes) fail(400, 'La clase se cruza con otra del horario. Revisa el dia, las horas o la frecuencia A/B.');
    const code = id => doc.courses.find(c => c.id === id)?.code || 'Curso';
    const from = cl.start > conflict.start ? cl.start : conflict.start, to = cl.end < conflict.end ? cl.end : conflict.end;
    notes.push(`${code(conflict.courseId)} y ${code(cl.courseId)} se cruzan el ${DAY_NAMES[cl.weekday - 1]} de ${from} a ${to}; se guardaron ambas clases.`);
  }
  const replan = doc => generatePlan(doc, limaNow(now()));

  return async function handler(request, context = {}) {
    try {
      if (!secret || secret.length < 32) fail(503, 'Falta configurar SESSION_SECRET en el servidor (minimo 32 caracteres).');
      const url = new URL(request.url);
      const route = url.pathname.replace(/^\/\.netlify\/functions\/api/, '').replace(/^\/api/, '') || '/';
      const method = request.method;
      if (!['GET', 'POST', 'PUT', 'DELETE'].includes(method)) fail(405, 'Metodo no permitido.');
      const write = method !== 'GET';
      if (write) {
        if (request.headers.get('origin') !== url.origin || request.headers.get('sec-fetch-site') === 'cross-site') fail(403, 'Solicitud de otro origen rechazada.');
      }
      if (route === '/health' && method === 'GET') { await db.query('SELECT 1'); return json({ status: 'UP' }); }
      if (['/login', '/register'].includes(route) && method === 'POST') {
        await limit('auth-ip:' + (context.ip || 'unknown'), 30, 900);
        const data = authSchema.parse(await body(request));
        await limit('auth-email:' + data.email, 12, 900);
        const { rows } = await db.query('SELECT id,email,password_hash FROM pa_netlify_users WHERE email=$1', [data.email]);
        let user = rows[0];
        if (route === '/register') {
          if (user) fail(409, 'Este correo ya tiene una cuenta.');
          user = { id: randomUUID(), email: data.email };
          const passwordHash = await bcrypt.hash(data.password, 12);
          const document = initialDocument(data.email);
          await db.transaction([
            ['INSERT INTO pa_netlify_users(id,email,password_hash) VALUES($1,$2,$3)', [user.id, user.email, passwordHash]],
            ['INSERT INTO pa_netlify_state(user_id,document) VALUES($1,$2::jsonb)', [user.id, JSON.stringify(document)]]
          ]);
        } else if (!(await bcrypt.compare(data.password, user?.password_hash || dummyHash)) || !user) fail(401, 'Correo o contraseña incorrectos.');
        const token = randomBytes(32).toString('hex');
        await db.query('INSERT INTO pa_netlify_sessions(token_hash,user_id,expires_at) VALUES($1,$2,$3)', [hash(token), user.id, new Date(now().valueOf() + 604800000)]);
        return json({ ok: true }, 200, { 'Set-Cookie': sessionCookie(token) });
      }

      const user = await authenticate(request);
      if (write && !safeEqual(request.headers.get('x-csrf-token'), csrf(user.token))) fail(403, 'La validacion de seguridad fallo. Vuelve a ingresar.');
      if (route === '/logout' && method === 'POST') {
        await db.query('DELETE FROM pa_netlify_sessions WHERE token_hash=$1', [hash(user.token)]);
        return json({ ok: true }, 200, { 'Set-Cookie': sessionCookie('', 0) });
      }
      if (route === '/session' && method === 'GET') {
        const today = limaNow(now()).date;
        if (user.document.lastPlanDate !== today && user.document.blocks.some(b => !b.completed && !b.reprogrammed && b.date < today)) {
          try { return json(await save(user, replan(user.document), user.version)); } catch (e) { if (e.status !== 409) throw e; return json(packet(await authenticate(request))); }
        }
        return json(packet(user));
      }
      if (route === '/export/calendar' && method === 'GET') return new Response(calendarIcs(user.document, now()), {
        headers: { ...headers, 'Content-Type': 'text/calendar; charset=utf-8', 'Content-Disposition': 'attachment; filename="mi-plan.ics"' }
      });
      if (route === '/export/data' && method === 'GET') return json({ format: 'planificador-netlify-v1', exportedAt: now().toISOString(), data: user.document }, 200, { 'Content-Disposition': 'attachment; filename="mis-datos-planificador.json"' });
      if (!write) fail(404, 'Ruta no encontrada.');
      const input = await body(request);
      await limit('writes:' + user.id, 120, 60);
      if (route === '/import/analyze' && method === 'POST') {
        await limit('ia:' + user.id, 12, 3600);
        return json(await analyze(importSchema.parse(input), env, user.document.settings.cycleStart, fetchAI));
      }
      const { version } = versionSchema.parse(input);
      if (version !== user.version) fail(409, 'Tus datos cambiaron en otra ventana. Actualiza antes de guardar.');
      let doc = structuredClone(user.document);
      const notes = [];
      if (route === '/settings' && method === 'PUT') {
        const settings = settingsSchema.parse(input.data);
        const shift = daysBetween(doc.settings.cycleStart, settings.cycleStart);
        if (shift) { doc.evaluations = doc.evaluations.map(e => ({ ...e, date: addDays(e.date, shift) })); doc.blocks = doc.blocks.filter(b => b.completed); }
        doc.settings = settings;
      } else if (route === '/plan' && method === 'POST') {
        // El plan se recalcula despues de validar la version.
      } else if (route === '/courses' && method === 'POST') {
        const data = courseSchema.parse(input.data);
        if (doc.courses.some(c => c.code === data.code)) fail(409, 'Ya tienes un curso con ese codigo.');
        doc.courses.push({ ...data, id: randomUUID() });
      } else if (/^\/courses\/[a-f\d-]+$/.test(route)) {
        const id = route.split('/')[2]; requireCourse(doc, id);
        if (method === 'DELETE') {
          const evIds = new Set(doc.evaluations.filter(e => e.courseId === id).map(e => e.id));
          doc.courses = doc.courses.filter(c => c.id !== id); doc.evaluations = doc.evaluations.filter(e => e.courseId !== id);
          doc.classes = doc.classes.filter(c => c.courseId !== id); doc.blocks = doc.blocks.filter(b => !evIds.has(b.evaluationId));
        } else if (method === 'PUT') {
          const data = courseSchema.parse(input.data);
          if (doc.courses.some(c => c.id !== id && c.code === data.code)) fail(409, 'El codigo ya pertenece a otro curso.');
          doc.courses = doc.courses.map(c => c.id === id ? { ...data, id } : c);
        } else fail(405, 'Metodo no permitido.');
      } else if (route === '/evaluations' && method === 'POST') {
        const data = evaluationSchema.parse(input.data); const course = requireCourse(doc, data.courseId);
        validateWeight(doc, data); data.estimatedHours ??= estimatedHours(data.type, course.difficulty, data.weight);
        doc.evaluations.push({ ...data, id: randomUUID() });
      } else if (/^\/evaluations\/[a-f\d-]+(?:\/toggle)?$/.test(route)) {
        const id = route.split('/')[2]; const ev = doc.evaluations.find(e => e.id === id) || fail(404, 'No se encontro el pendiente.');
        if (route.endsWith('/toggle') && method === 'POST') ev.completed = !ev.completed;
        else if (method === 'DELETE') { doc.evaluations = doc.evaluations.filter(e => e.id !== id); doc.blocks = doc.blocks.filter(b => b.evaluationId !== id); }
        else if (method === 'PUT') {
          const data = evaluationSchema.parse({ ...input.data, origin: input.data.origin ?? ev.origin ?? 'AGENDA', week: input.data.week ?? ev.week ?? null }); const course = requireCourse(doc, data.courseId);
          validateWeight(doc, data, id); data.estimatedHours ??= estimatedHours(data.type, course.difficulty, data.weight);
          doc.evaluations = doc.evaluations.map(e => e.id === id ? { ...data, id } : e);
        } else fail(405, 'Metodo no permitido.');
      } else if (route === '/classes' && method === 'POST') {
        const cl = classSchema.parse(input.data); requireCourse(doc, cl.courseId); validateClass(doc, cl);
        doc.classes.push({ ...cl, id: randomUUID() });
      } else if (/^\/classes\/[a-f\d-]+$/.test(route)) {
        const id = route.split('/')[2]; if (!doc.classes.some(c => c.id === id)) fail(404, 'No se encontro la clase.');
        if (method === 'DELETE') doc.classes = doc.classes.filter(c => c.id !== id);
        else if (method === 'PUT') {
          const cl = classSchema.parse(input.data); requireCourse(doc, cl.courseId); validateClass(doc, cl, id);
          doc.classes = doc.classes.map(c => c.id === id ? { ...cl, id } : c);
        } else fail(405, 'Metodo no permitido.');
      } else if (/^\/blocks\/[a-f\d-]+\/toggle$/.test(route) && method === 'POST') {
        const block = doc.blocks.find(b => b.id === route.split('/')[2]) || fail(404, 'No se encontro el bloque.');
        // Render permite marcar un bloque atrasado si el estudiante si lo realizo.
        if (block.reprogrammed) block.reprogrammed = false;
        block.completed = !block.completed;
      } else if (route === '/import/save' && method === 'POST') {
        const mode = z.enum(['silabo', 'horario']).parse(input.kind);
        const addCourse = raw => {
          const data = courseSchema.parse(raw);
          const existing = doc.courses.find(c => c.code === data.code);
          if (existing) { Object.assign(existing, { ...data, topics: mode === 'silabo' ? data.topics : existing.topics, topicSchedule: mode === 'silabo' ? data.topicSchedule : existing.topicSchedule || [], difficulty: mode === 'horario' ? existing.difficulty : data.difficulty }); return existing; }
          const c = { ...data, id: randomUUID() }; doc.courses.push(c); return c;
        };
        if (mode === 'silabo') {
          const course = addCourse(input.course);
          const entries = z.array(evaluationSchema.omit({ courseId: true })).max(150).parse(input.evaluations);
          const replacedIds = new Set(doc.evaluations.filter(e => e.courseId === course.id && e.origin === 'SILABO').map(e => e.id));
          doc.evaluations = doc.evaluations.filter(e => !replacedIds.has(e.id));
          doc.blocks = doc.blocks.filter(b => !replacedIds.has(b.evaluationId));
          for (const entry of entries) {
            if (doc.evaluations.some(e => e.courseId === course.id && e.name === entry.name && e.date === entry.date)) continue;
            const data = { ...entry, origin: 'SILABO', courseId: course.id, id: randomUUID() };
            validateWeight(doc, data); data.estimatedHours ??= estimatedHours(data.type, course.difficulty, data.weight);
            doc.evaluations.push(data);
          }
          // Horario de clases del silabo (como RegistroSilaboService en Render): si el curso
          // ya tenia horario importado, ese horario manda y estas filas se ignoran.
          const { weekday: rowDay, start: rowStart, end: rowEnd } = classSchema.shape;
          const rows = z.array(z.object({ weekday: rowDay, start: rowStart, end: rowEnd })).max(40).parse(input.classes || []);
          if (rows.length && !doc.classes.some(c => c.courseId === course.id)) for (const row of rows) {
            const cl = classSchema.parse({ ...row, courseId: course.id, frequency: 'SEMANAL', detail: '' });
            if (doc.classes.some(c => c.courseId === cl.courseId && c.weekday === cl.weekday && c.start === cl.start && c.end === cl.end)) continue;
            validateClass(doc, cl, undefined, notes); doc.classes.push({ ...cl, id: randomUUID() });
          }
        } else {
          const courses = z.array(courseSchema).min(1).max(40).parse(input.courses).map(addCourse);
          if (new Set(courses.map(c => c.id)).size !== courses.length) fail(400, 'Hay codigos repetidos en la lista de cursos.');
          const { courseId: ignored, ...classFields } = classSchema.shape;
          const entries = z.array(z.object({ ...classFields, code: z.string().max(24) })).max(150).parse(input.classes);
          const replacedCourses = new Set(courses.map(c => c.id));
          doc.classes = doc.classes.filter(c => !replacedCourses.has(c.courseId));
          for (const entry of entries) {
            const course = courses.find(c => c.code === entry.code.toUpperCase().trim()) || fail(400, 'La clase no coincide con ningun curso.');
            const cl = classSchema.parse({ ...entry, courseId: course.id });
            if (doc.classes.some(c => c.courseId === cl.courseId && c.weekday === cl.weekday && c.start === cl.start && c.end === cl.end && c.frequency === cl.frequency)) continue;
            validateClass(doc, cl, undefined, notes); doc.classes.push({ ...cl, id: randomUUID() });
          }
        }
        alignSyllabusDates(doc);
      } else fail(404, 'Ruta no encontrada.');
      doc = replan(doc);
      return json({ ...(await save(user, doc, version)), notes });
    } catch (error) {
      if (error instanceof z.ZodError) return json({ error: error.issues[0]?.message || 'Revisa los datos del formulario.' }, 400);
      if (error.code === '23505') return json({ error: 'El registro ya existe. Actualiza antes de continuar.' }, 409);
      if (error.status) return json({ error: error.message, ...(error.providerRateLimited ? { retryAfter: error.retryAfter, providerRateLimited: true } : {}) }, error.status, error.status === 429 ? { 'Retry-After': String(error.retryAfter || 60) } : {});
      console.warn('Error interno del planificador', error.code || error.name);
      return json({ error: error.code === '42P01' ? 'La base no esta preparada. Ejecuta db:prepare en la base de la nueva version.' : 'No se pudo conectar con el servidor. Tus cambios no se han guardado.' }, 503);
    }
  };
}

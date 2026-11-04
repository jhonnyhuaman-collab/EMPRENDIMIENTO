import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { localDatabase } from '../scripts/local-db.mjs';
import { createHandler } from '../server/api.mjs';

let db, handler, count = 0;
const env = { SESSION_SECRET: 'prueba-local-solo-test-'.repeat(3), GROQ_API_KEY: 'test-not-a-real-key', IA_PROVEEDOR: 'auto' };
const now = () => new Date('2026-11-04T15:30:00Z');
const aiResult = { course: { code: 'CI01', name: 'Control Inteligente', credits: 4, difficulty: 4, color: '#235B4E', topics: ['Redes neuronales'] }, evaluations: [{ name: 'EC1', type: 'EXAMEN', week: 15, weekday: 2, weight: 20, topics: [] }], warnings: [] };
before(async () => {
  db = await localDatabase();
  handler = createHandler({ db, env, now, fetchAI: async () => Response.json({ choices: [{ message: { content: JSON.stringify(aiResult) } }] }) });
});
after(async () => { await db.close(); });
async function call(path, method = 'GET', body, account, overrides = {}) {
  const headers = { origin: 'https://app.example', ...(body ? { 'content-type': 'application/json' } : {}), ...(account ? { cookie: account.cookie, 'x-csrf-token': account.csrfToken } : {}), ...overrides };
  const response = await handler(new Request('https://app.example/api' + path, { method, headers, ...(body ? { body: JSON.stringify(body) } : {}) }), { ip: 'test-' + (account?.email || 'anonymous') });
  const result = response.headers.get('content-type')?.startsWith('application/json') ? await response.json() : await response.text();
  return { response, result };
}
async function account() {
  const email = `test${count++}@example.com`;
  const { response } = await call('/register', 'POST', { email, password: 'ClaveSegura2026' });
  assert.equal(response.status, 200);
  const cookie = response.headers.get('set-cookie').split(';')[0];
  const { result } = await call('/session', 'GET', null, { cookie });
  return { ...result, cookie, email };
}
const course = { code: 'MA01', name: 'Matemáticas', credits: 4, difficulty: 3, color: '#235B4E', topics: ['Integrales'] };

test('requiere autenticacion y no expone datos de otra cuenta', async () => {
  assert.equal((await call('/session')).response.status, 401);
  const a = await account(), b = await account();
  const created = await call('/courses', 'POST', { version: a.version, data: course }, a);
  assert.equal(created.response.status, 200);
  assert.equal((await call('/session', 'GET', null, b)).result.state.courses.length, 0);
  assert.equal((await call('/courses/' + created.result.state.courses[0].id, 'DELETE', { version: b.version }, b)).response.status, 404);
});
test('registro guarda BCrypt, no la clave en texto y cookie HttpOnly', async () => {
  const a = await account();
  const stored = (await db.query('SELECT password_hash FROM pa_netlify_users WHERE email=$1', [a.email])).rows[0];
  assert.match(stored.password_hash, /^\$2[ab]\$12\$/);
  assert.ok(!JSON.stringify(a.state).includes('ClaveSegura2026'));
  const login = await call('/login', 'POST', { email: a.email, password: 'ClaveSegura2026' });
  assert.match(login.response.headers.get('set-cookie'), /HttpOnly/);
  assert.match(login.response.headers.get('set-cookie'), /SameSite=Lax/);
  assert.equal((await call('/login', 'POST', { email: a.email, password: 'equivocada' })).response.status, 401);
});
test('rechaza CSRF, origen ajeno y claves truncables por BCrypt', async () => {
  const a = await account();
  assert.equal((await call('/courses', 'POST', { version: a.version, data: course }, a, { 'x-csrf-token': '' })).response.status, 403);
  assert.equal((await call('/courses', 'POST', { version: a.version, data: course }, a, { 'x-csrf-token': 'á'.repeat(64) })).response.status, 403);
  assert.equal((await call('/courses', 'POST', { version: a.version, data: course }, a, { origin: 'https://attacker.example' })).response.status, 403);
  assert.equal((await call('/register', 'POST', { email: 'long@example.com', password: 'á'.repeat(50) })).response.status, 400);
});
test('la sesion sobrevive a una instancia nueva de la funcion', async () => {
  const a = await account();
  const newInstance = createHandler({ db, env, now });
  const response = await newInstance(new Request('https://app.example/api/session', { headers: { cookie: a.cookie } }));
  assert.equal(response.status, 200); assert.equal((await response.json()).user.email, a.email);
});
test('cierre de sesion revoca el token aunque el navegador conserve la cookie', async () => {
  const a = await account();
  assert.equal((await call('/logout', 'POST', {}, a)).response.status, 200);
  assert.equal((await call('/session', 'GET', null, a)).response.status, 401);
});
test('las sesiones vencidas no se aceptan', async () => {
  const a = await account();
  await db.query('UPDATE pa_netlify_sessions SET expires_at=$1 WHERE user_id=$2', [new Date('2020-01-01'), a.user.id]);
  assert.equal((await call('/session', 'GET', null, a)).response.status, 401);
});
test('control optimista evita pisar una edicion de otra ventana', async () => {
  const a = await account();
  assert.equal((await call('/courses', 'POST', { version: a.version, data: course }, a)).response.status, 200);
  assert.equal((await call('/courses', 'POST', { version: a.version, data: { ...course, code: 'OTRO' } }, a)).response.status, 409);
});
test('guardar evaluacion genera plan; notas, borrado en cascada y exportacion', async () => {
  const a = await account();
  const created = await call('/courses', 'POST', { version: a.version, data: course }, a); Object.assign(a, created.result);
  const id = a.state.courses[0].id;
  const evaluated = await call('/evaluations', 'POST', { version: a.version, data: { courseId: id, name: 'PC1', type: 'PRACTICA', date: '2026-11-20', weight: 20, grade: 18 } }, a);
  assert.equal(evaluated.response.status, 200); Object.assign(a, evaluated.result);
  assert.ok(a.state.blocks.length); assert.equal(a.state.evaluations[0].grade, 18);
  assert.match((await call('/export/calendar', 'GET', null, a)).result, /BEGIN:VCALENDAR/);
  assert.equal((await call('/export/data', 'GET', null, a)).result.data.courses.length, 1);
  const deleted = await call('/courses/' + id, 'DELETE', { version: a.version }, a);
  assert.equal(deleted.result.state.evaluations.length, 0); assert.equal(deleted.result.state.blocks.length, 0);
});
test('rechaza pesos superiores al 100%, curso ajeno y clases que se cruzan', async () => {
  const a = await account(), b = await account();
  Object.assign(a, (await call('/courses', 'POST', { version: a.version, data: course }, a)).result);
  const id = a.state.courses[0].id;
  const ev = { courseId: id, name: 'Final', type: 'EXAMEN', date: '2026-11-20', weight: 100 };
  assert.equal((await call('/evaluations', 'POST', { version: b.version, data: ev }, b)).response.status, 404);
  Object.assign(a, (await call('/evaluations', 'POST', { version: a.version, data: ev }, a)).result);
  assert.equal((await call('/evaluations', 'POST', { version: a.version, data: { ...ev, weight: 1 } }, a)).response.status, 400);
  const cl = { courseId: id, weekday: 1, start: '10:00', end: '12:00', frequency: 'A' };
  Object.assign(a, (await call('/classes', 'POST', { version: a.version, data: cl }, a)).result);
  assert.equal((await call('/classes', 'POST', { version: a.version, data: { ...cl, start: '11:00' } }, a)).response.status, 400);
  assert.equal((await call('/classes', 'POST', { version: a.version, data: { ...cl, frequency: 'B' } }, a)).response.status, 200);
});
test('no guarda resultados de IA hasta que el usuario los confirma', async () => {
  const a = await account();
  const analysis = await call('/import/analyze', 'POST', { kind: 'silabo', text: 'Texto de prueba de un silabo con evaluaciones.' }, a);
  assert.equal(analysis.response.status, 200); assert.equal(analysis.result.evaluations[0].date, '2026-11-17');
  assert.equal((await call('/session', 'GET', null, a)).result.state.courses.length, 0);
  const saved = await call('/import/save', 'POST', { version: a.version, kind: 'silabo', course: analysis.result.course, evaluations: analysis.result.evaluations }, a);
  assert.equal(saved.response.status, 200); assert.equal(saved.result.state.evaluations.length, 1);
});
test('importacion de horario funciona y respeta las referencias', async () => {
  const a = await account();
  const saved = await call('/import/save', 'POST', { version: a.version, kind: 'horario', courses: [course], classes: [{ code: course.code, weekday: 1, start: '10:00', end: '12:00', frequency: 'SEMANAL' }] }, a);
  assert.equal(saved.response.status, 200); assert.equal(saved.result.state.classes.length, 1);
  assert.equal(saved.result.state.classes[0].courseId, saved.result.state.courses[0].id);
});
test('limitacion de intentos persiste en PostgreSQL', async () => {
  for (let i = 0; i < 13; i++) {
    const result = await call('/login', 'POST', { email: 'unknown@example.com', password: 'ClaveSegura2026' });
    assert.equal(result.response.status, i < 12 ? 401 : 429);
  }
});
test('en produccion la cookie es Secure y no se aceptan secretos ausentes', async () => {
  const a = await account();
  const production = createHandler({ db, env: { ...env, NETLIFY: 'true' }, now });
  const response = await production(new Request('https://app.example/api/login', { method: 'POST', headers: { origin: 'https://app.example', 'content-type': 'application/json' }, body: JSON.stringify({ email: a.email, password: 'ClaveSegura2026' }) }), { ip: 'production' });
  assert.match(response.headers.get('set-cookie'), /__Host-pa_session=/); assert.match(response.headers.get('set-cookie'), /; Secure/);
  const missing = createHandler({ db, env: {} }); assert.equal((await missing(new Request('https://app.example/api/session'))).status, 503);
});

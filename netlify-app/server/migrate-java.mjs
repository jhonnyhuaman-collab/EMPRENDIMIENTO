import { randomUUID } from 'node:crypto';
import { initialDocument, settingsSchema, courseSchema, evaluationSchema, classSchema, addDays, time } from './domain.mjs';

const day = { MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6, SUNDAY: 7 };
const date = value => value instanceof Date ? value.toISOString().slice(0, 10) : String(value).slice(0, 10);
const hour = value => String(value).slice(0, 5);

export function convertJava(data) {
  const reports = [];
  for (const student of data.students.filter(s => s.email && s.password_hash)) {
    if (!/^\$2[aby]\$/.test(student.password_hash)) throw new Error('Se encontro una clave no compatible con BCrypt.');
    const document = initialDocument(student.email);
    const old = document.settings;
    document.settings = settingsSchema.parse({
      ...old, name: student.nombre || old.name,
      cycleStart: student.fecha_inicio_ciclo ? date(student.fecha_inicio_ciclo) : old.cycleStart,
      dailyHours: student.horas_estudio_por_dia ?? old.dailyHours,
      totalDailyHours: student.carga_maxima_diaria ?? old.totalDailyHours,
      weeklyCourseHours: student.horas_maximas_curso_semana ?? old.weeklyCourseHours,
      sleepTime: student.hora_dormir ? hour(student.hora_dormir) : old.sleepTime,
      studyDays: student.dias_estudio ? student.dias_estudio.split(',').map(s => day[s.trim()]).filter(Boolean) : old.studyDays
    });
    const courseMap = new Map(), evalMap = new Map();
    for (const c of data.courses.filter(c => String(c.estudiante_id) === String(student.id))) {
      const id = randomUUID(); courseMap.set(String(c.id), id);
      const topics = data.topics.filter(t => String(t.curso_id) === String(c.id)).map(t => `${t.numero ? t.numero + ' ' : ''}${t.titulo}`);
      document.courses.push({ ...courseSchema.parse({ code: c.codigo, name: c.nombre, credits: c.creditos || 3, difficulty: c.dificultad || 3, color: c.color || '#235B4E', topics }), id });
    }
    for (const e of data.evaluations.filter(e => courseMap.has(String(e.curso_id)))) {
      const id = randomUUID(); evalMap.set(String(e.id), id);
      const topicIds = data.links.filter(l => String(l.evaluacion_id) === String(e.id)).map(l => String(l.tema_id));
      document.evaluations.push({ ...evaluationSchema.parse({
        courseId: courseMap.get(String(e.curso_id)), name: e.nombre, type: e.tipo,
        date: e.fecha ? date(e.fecha) : addDays(document.settings.cycleStart, ((e.semana || 1) - 1) * 7),
        time: e.hora_entrega ? hour(e.hora_entrega) : null, weight: e.peso_en_nota_final || 0,
        estimatedHours: e.horas_estimadas ?? null, grade: e.nota ?? null, completed: !!e.completada,
        notes: e.notas || '', topics: data.topics.filter(t => topicIds.includes(String(t.id))).map(t => t.titulo)
      }), id });
    }
    for (const cl of data.classes.filter(c => courseMap.has(String(c.curso_id)))) document.classes.push({
      ...classSchema.parse({ courseId: courseMap.get(String(cl.curso_id)), weekday: day[cl.dia_semana], start: hour(cl.hora_inicio), end: hour(cl.hora_fin), frequency: !cl.frecuencia || cl.frecuencia === 'SEMANAL' ? 'SEMANAL' : cl.frecuencia.endsWith('A') ? 'A' : 'B', detail: cl.detalle || '' }), id: randomUUID()
    });
    for (const b of data.blocks.filter(b => evalMap.has(String(b.evaluacion_id)))) document.blocks.push({
      id: randomUUID(), evaluationId: evalMap.get(String(b.evaluacion_id)), date: date(b.fecha),
      start: time.parse(hour(b.hora_inicio)), end: time.parse(hour(b.hora_fin)),
      description: b.descripcion || 'Estudio', completed: !!b.completado, reprogrammed: !!b.reprogramado
    });
    reports.push({ email: student.email.trim().toLowerCase(), passwordHash: student.password_hash, document });
  }
  return reports;
}

export function assertSeparateDatabases(source, target) {
  if (!source || !target) throw new Error('Configura JAVA_DATABASE_URL y DATABASE_URL.');
  const a = new URL(source), b = new URL(target);
  if (a.hostname.replace('-pooler.', '.') === b.hostname.replace('-pooler.', '.') && a.pathname === b.pathname) {
    throw new Error('Origen y destino deben ser bases diferentes. No se copian datos sobre la base activa de Java.');
  }
}

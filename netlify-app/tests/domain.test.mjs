import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { initialDocument, settingsSchema, freeSlots, minutes, weekday, monday, estimatedHours, academicWeek, limaNow } from '../server/domain.mjs';
import { generatePlan, calendarIcs } from '../server/planner.mjs';
import { convertJava, assertSeparateDatabases } from '../server/migrate-java.mjs';

function fixture() {
  const doc = initialDocument('ana.perez@example.com');
  doc.courses = [{ id: randomUUID(), code: 'MT5307', name: 'Control', difficulty: 4, credits: 4, color: '#235B4E', topics: [] }];
  doc.evaluations = [{ id: randomUUID(), courseId: doc.courses[0].id, name: 'Examen', type: 'EXAMEN', date: '2026-11-20', time: '16:00', weight: 20, estimatedHours: 10, completed: false, grade: null, topics: ['Lógica difusa'], notes: '' }];
  doc.classes = [{ id: randomUUID(), courseId: doc.courses[0].id, weekday: 3, start: '08:00', end: '16:00', frequency: 'SEMANAL' }];
  return doc;
}
test('fechas y zona horaria de Lima, sin depender del huso del servidor', () => {
  assert.equal(limaNow(new Date('2026-11-05T02:00:00Z')).date, '2026-11-04');
  assert.equal(academicWeek('2026-08-10', '2026-10-19'), 11);
  assert.equal(weekday('2026-11-08'), 7); assert.equal(estimatedHours('EXAMEN', 4, 20), 17);
});
test('rechaza no elegir dias y ciclos que no empiezan el lunes', () => {
  const s = fixture().settings;
  assert.equal(settingsSchema.safeParse({ ...s, studyDays: [] }).success, false);
  assert.equal(settingsSchema.safeParse({ ...s, cycleStart: '2026-08-11' }).success, false);
});
test('planifica sin cruces, con descansos, sin fines de semana y antes de dormir', () => {
  const doc = generatePlan(fixture(), { date: '2026-11-04', minutes: 10 * 60 + 11 });
  assert.ok(doc.blocks.length);
  for (const b of doc.blocks) {
    assert.ok(b.date < '2026-11-20'); assert.ok(weekday(b.date) <= 5);
    assert.ok(minutes(b.end) <= 22 * 60); assert.ok(minutes(b.end) - minutes(b.start) <= 90);
    if (b.date === '2026-11-04') assert.ok(minutes(b.start) >= 10 * 60 + 30);
    for (const c of doc.classes.filter(c => c.weekday === weekday(b.date))) assert.ok(b.end <= c.start || minutes(b.start) >= minutes(c.end) + 15);
    for (const other of doc.blocks.filter(o => o.id !== b.id && o.date === b.date && o.start >= b.start)) assert.ok(minutes(other.start) >= minutes(b.end) + 15);
  }
  for (const date of new Set(doc.blocks.map(b => b.date))) assert.ok(doc.blocks.filter(b => b.date === date).reduce((s, b) => s + minutes(b.end) - minutes(b.start), 0) <= 180);
  for (const week of new Set(doc.blocks.map(b => monday(b.date)))) assert.ok(doc.blocks.filter(b => monday(b.date) === week).reduce((s, b) => s + minutes(b.end) - minutes(b.start), 0) <= 240);
});
test('conserva bloques completados y no los duplica al regenerar', () => {
  const doc = fixture(); const completed = { id: randomUUID(), evaluationId: doc.evaluations[0].id, date: '2026-11-04', start: '17:00', end: '18:00', completed: true, reprogrammed: false };
  doc.blocks.push(completed);
  const result = generatePlan(doc, { date: '2026-11-04', minutes: 600 });
  assert.deepEqual(result.blocks.find(b => b.id === completed.id), completed);
  const next = generatePlan(result, { date: '2026-11-04', minutes: 600 });
  assert.equal(next.blocks.filter(b => b.completed).length, 1);
  assert.equal(next.blocks.length, result.blocks.length);
});
test('no estudia evaluaciones completadas y avisa cuando no alcanza el tiempo', () => {
  const doc = fixture(); doc.evaluations[0].completed = true;
  assert.equal(generatePlan(doc, { date: '2026-11-04', minutes: 600 }).blocks.length, 0);
  doc.evaluations[0].completed = false; doc.evaluations[0].date = '2026-11-05'; doc.settings.dailyHours = .5;
  assert.ok(generatePlan(doc, { date: '2026-11-04', minutes: 23 * 60 }).warnings.length);
});
test('clases A/B y hora de dormir posterior a medianoche', () => {
  const s = fixture().settings;
  const classes = [{ weekday: 3, start: '08:00', end: '10:00', frequency: 'B' }];
  assert.equal(freeSlots('2026-11-04', 480, s, classes, [])[0][0], 480);
  assert.equal(freeSlots('2026-11-11', 480, s, classes, [])[0][0], 615);
  assert.equal(freeSlots('2026-11-04', 480, { ...s, sleepTime: '01:00' }, [], []).at(-1)[1], 1380);
});
test('ICS escapa caracteres, fecha sin hora y plegado UTF8 de 75 bytes', () => {
  const doc = fixture(); doc.evaluations[0].name = 'Á'.repeat(100) + ', prueba; examen'; doc.evaluations[0].time = null;
  const result = calendarIcs(doc, new Date('2026-11-04T12:00:00Z'));
  assert.match(result, /DTSTART;VALUE=DATE:20261120/); assert.match(result.replace(/\r\n /g, ''), /\\, prueba\\; examen/);
  assert.ok(result.split('\r\n').every(line => Buffer.byteLength(line) <= 75));
});
test('conversion de Java conserva BCrypt, cuentas, notas y referencias propias', () => {
  const source = { students: [{ id: 1, email: 'ana@example.com', password_hash: '$2a$10$preservado', nombre: 'Ana' }, { id: 2, email: 'otro@example.com', password_hash: '$2b$10$otro' }],
    courses: [{ id: 10, estudiante_id: 1, codigo: 'MAT', nombre: 'Matemáticas', creditos: 4, dificultad: 3 }],
    topics: [{ id: 20, curso_id: 10, titulo: 'Derivadas' }],
    evaluations: [{ id: 30, curso_id: 10, nombre: 'PC1', tipo: 'PRACTICA', fecha: '2026-11-10', peso_en_nota_final: 20, nota: 18 }],
    classes: [], blocks: [{ id: 40, evaluacion_id: 30, fecha: '2026-11-05', hora_inicio: '18:00:00', hora_fin: '19:00:00', completado: true }], links: [{ evaluacion_id: 30, tema_id: 20 }] };
  const result = convertJava(source);
  assert.equal(result[0].passwordHash, source.students[0].password_hash);
  assert.equal(result[0].document.evaluations[0].grade, 18); assert.deepEqual(result[0].document.evaluations[0].topics, ['Derivadas']);
  assert.equal(result[0].document.blocks[0].evaluationId, result[0].document.evaluations[0].id);
  assert.equal(result[1].document.courses.length, 0);
});
test('ICS permite una entrega cuya hora final cruza medianoche', () => {
  const doc = fixture(); doc.evaluations[0].time = '23:30';
  assert.match(calendarIcs(doc), /DTEND;TZID=America\/Lima:20261121T003000/);
});
test('no permite copiar sobre la misma base activa ni aunque cambie el pooler', () => {
  assert.throws(() => assertSeparateDatabases('postgresql://a:b@ep-demo-pooler.neon.tech/old', 'postgresql://a:b@ep-demo.neon.tech/old'));
  assert.doesNotThrow(() => assertSeparateDatabases('postgresql://a:b@ep-demo.neon.tech/old', 'postgresql://a:b@ep-demo.neon.tech/new'));
});

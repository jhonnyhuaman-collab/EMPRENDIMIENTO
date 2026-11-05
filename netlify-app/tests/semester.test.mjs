import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generatePlan } from '../server/planner.mjs';
import { semesterSteps, orderedTopics } from '../server/semester.mjs';
import { initialDocument, addDays, academicWeek, minutes } from '../server/domain.mjs';
function fixture(hours = 24, count = 16) {
  const doc = initialDocument('estudiante@example.test');
  doc.settings.cycleStart = '2026-08-10';
  const topics = Array.from({ length: count }, (_, i) => `${i + 1}.1 Tema ${i + 1}`);
  doc.courses = [{ id: 'c', code: 'CI', name: 'Control', credits: 4, difficulty: 3, color: '#235b4e', topics, topicSchedule: [] }];
  doc.evaluations = [{ id: 'e', courseId: 'c', name: 'Final', type: 'EXAMEN', date: addDays(doc.settings.cycleStart, 15 * 7 + 4), weight: 25, estimatedHours: hours, topics, completed: false }];
  return doc;
}
const now = { date: '2026-08-10', minutes: 480 };
test('prepara el final a lo largo del ciclo y termina con repaso en la ultima semana', () => {
  const doc = fixture(), result = generatePlan(doc, now), blocks = result.blocks;
  const weeks = new Set(blocks.map(b => academicWeek(doc.settings.cycleStart, b.date)));
  assert.ok(weeks.size >= 12, `Semanas usadas: ${[...weeks]}`);
  assert.ok(weeks.has(1)); assert.ok(weeks.has(16));
  assert.ok(blocks.every(b => b.date < doc.evaluations[0].date && b.date < addDays(doc.settings.cycleStart, 112)));
  assert.equal(blocks.at(-1).phase, 'simulacro');
  const total = blocks.reduce((s, b) => s + (minutes(b.end) - minutes(b.start)) / 60, 0);
  assert.equal(total, 24);
  const topics = blocks.filter(b => b.phase === 'tema').flatMap(b => b.topics);
  assert.deepEqual([...new Set(topics)], doc.courses[0].topics);
});
test('muchos temas y pocas horas se agrupan sin perder los ultimos temas ni el simulacro', () => {
  const doc = fixture(6, 32), result = generatePlan(doc, now);
  assert.deepEqual([...new Set(result.blocks.filter(b => b.phase === 'tema').flatMap(b => b.topics))], doc.courses[0].topics);
  assert.ok(result.blocks.some(b => b.phase === 'simulacro'));
  assert.equal(result.blocks.reduce((s, b) => s + (minutes(b.end) - minutes(b.start)) / 60, 0), 6);
});
test('el presupuesto de pasos suma las horas exactas, no media hora extra por cada tema', () => {
  const doc = fixture(6, 32), e = doc.evaluations[0];
  const steps = semesterSteps(e, doc.courses[0], now.date, e.date, doc.settings);
  assert.equal(steps.reduce((s, step) => s + step.hours, 0), 6);
});
test('no adelanta un tema antes de la semana indicada en el silabo', () => {
  const doc = fixture(12, 3); doc.courses[0].topicSchedule = doc.courses[0].topics.map((title, i) => ({ title, week: [1, 5, 10][i] }));
  const result = generatePlan(doc, now);
  for (const b of result.blocks.filter(b => b.phase === 'tema')) for (const title of b.topics) assert.ok(academicWeek(doc.settings.cycleStart, b.date) >= doc.courses[0].topicSchedule.find(t => t.title === title).week);
});
test('cubre semanas restantes sin crear estudio retroactivo y conserva lo completado', () => {
  const doc = fixture(); doc.blocks = [{ id: 'done', evaluationId: 'e', date: '2026-08-12', start: '08:00', end: '09:00', description: '1.1 Tema 1', completed: true, reprogrammed: false }];
  const today = { date: '2026-11-05', minutes: 600 }, result = generatePlan(doc, today);
  assert.ok(result.blocks.some(b => b.id === 'done'));
  assert.ok(result.blocks.filter(b => !b.completed).every(b => b.date >= today.date));
});
test('examen parcial y final tienen preparaciones ordenadas y repasos propios', () => {
  const doc = fixture(); const final = doc.evaluations[0];
  doc.evaluations.unshift({ ...final, id: 'partial', name: 'Parcial', date: addDays(doc.settings.cycleStart, 7 * 7 + 4), estimatedHours: 12, topics: final.topics.slice(0, 8) });
  const result = generatePlan(doc, now);
  assert.ok(result.blocks.filter(b => b.evaluationId === 'partial').every(b => b.date < doc.evaluations[0].date));
  assert.ok(result.blocks.filter(b => b.evaluationId === 'e').every(b => b.date > doc.evaluations[0].date));
  for (const id of ['partial', 'e']) assert.ok(result.blocks.some(b => b.evaluationId === id && b.phase === 'simulacro'));
});
test('usa temas del curso con aviso si la evaluacion no tiene temas asociados', () => {
  const doc = fixture(12, 4); doc.evaluations[0].topics = [];
  const result = generatePlan(doc, now);
  assert.ok(result.blocks.some(b => b.topics.includes('1.1 Tema 1')));
  assert.ok(result.warnings.some(w => /se proponen/.test(w)));
});
test('ordena numeracion natural y resuelve numeros de tema a su titulo completo', () => {
  const course = { topics: ['1.10 Tema diez', '1.2 Tema dos'] };
  assert.deepEqual(orderedTopics({ topics: ['1.10', '1.2'] }, course), ['1.2 Tema dos', '1.10 Tema diez']);
});

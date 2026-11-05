import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reviewPlan, recommendedStart, alignSyllabusDates, priority } from '../server/review.mjs';

test('repaso conserva los pasos y porcentajes del servicio Java', () => {
  const e = { type: 'LABORATORIO', estimatedHours: 10, topics: [] };
  assert.deepEqual(reviewPlan(e).map(p => [p.title, p.hours]), [['Leer la guía', 2], ['Preparar la práctica', 5], ['Redactar el informe', 3]]);
  const topics = reviewPlan({ ...e, topics: ['1.1 Teoria', '1.2 Practica'] });
  assert.deepEqual(topics.map(p => p.hours), [4, 4, 2]);
  assert.equal(topics[2].title, 'Simulacro final');
});
test('inicio recomendado cuenta solo dias elegidos como en Render', () => {
  assert.equal(recommendedStart({ date: '2026-11-16', estimatedHours: 6 }, [1, 2, 3, 4, 5]), '2026-11-09');
  assert.equal(priority({ date: '2026-11-05', weight: 40 }, { difficulty: 5 }, '2026-11-04'), 'alta');
});
test('teoria y laboratorio respetan semana A/B sin mover pendientes manuales', () => {
  const doc = { settings: { cycleStart: '2026-08-10' }, classes: [{ courseId: 'x', weekday: 2, start: '10:00', frequency: 'SEMANAL', detail: 'Teoria' }, { courseId: 'x', weekday: 4, start: '14:00', frequency: 'A', detail: 'Laboratorio' }], evaluations: [{ courseId: 'x', origin: 'SILABO', type: 'EXAMEN', week: 15 }, { courseId: 'x', origin: 'SILABO', type: 'LABORATORIO', week: 15 }, { courseId: 'x', origin: 'AGENDA', type: 'TAREA', week: 15, date: '2026-11-20', time: '23:59' }] };
  alignSyllabusDates(doc);
  assert.equal(doc.evaluations[0].date, '2026-11-17'); assert.equal(doc.evaluations[0].time, '10:00');
  assert.equal(doc.evaluations[1].date, '2026-11-19'); assert.equal(doc.evaluations[1].time, '14:00');
  assert.equal(doc.evaluations[2].date, '2026-11-20'); assert.equal(doc.evaluations[2].time, '23:59');
});

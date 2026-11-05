import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { generatePlan } from '../server/planner.mjs';
import { evaluationSchema } from '../server/domain.mjs';
import { formatPercent } from '../src/percent.mjs';
const fixtures = JSON.parse(await readFile(new URL('./fixtures/render-planner.json', import.meta.url), 'utf8'));
for (const scenario of fixtures) test(`paridad con Java original: ${scenario.name}`, () => {
  const result = generatePlan(scenario.document, scenario.now);
  const actual = result.blocks.filter(b => b.date >= scenario.now.date).map(b => ({ evaluationId: b.evaluationId, date: b.date, start: b.start, end: b.end, description: b.description.trim(), completed: b.completed })).sort((a, b) => a.date.localeCompare(b.date) || a.start.localeCompare(b.start) || a.evaluationId.localeCompare(b.evaluationId));
  assert.deepEqual(actual, scenario.expected);
});
test('porcentajes decimales se guardan y muestran sin redondear a una decima', () => {
  for (const weight of [.25, .001, 19.875, 33.3333, 100]) {
    const entry = evaluationSchema.parse({ courseId: '00000000-0000-4000-8000-000000000001', name: 'Parcial', type: 'EXAMEN', date: '2026-11-20', weight });
    assert.equal(entry.weight, weight);
    assert.equal(formatPercent(weight), String(weight));
  }
});

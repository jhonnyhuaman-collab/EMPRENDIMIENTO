import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pageText } from '../src/pdf-layout.mjs';
const word = (str, x, y, width = 40) => ({ str, transform: [1, 0, 0, 1, x, y], width });
test('reconstruye tabla de horarios aunque las columnas vengan desordenadas', () => {
  assert.equal(pageText([word('Martes', 180, 700), word('10:00', 300, 680), word('MT5307', 20, 680), word('Curso', 20, 700), word('Lunes', 180, 680)]), 'Curso | Martes\nMT5307 | Lunes | 10:00');
});
test('conserva palabras de una linea y descarta elementos sin coordenadas', () => {
  assert.equal(pageText([word('Control', 20, 700), word('Inteligente', 65, 701), { str: 'sin posicion' }, { type: 'markedContent' }]), 'Control Inteligente');
});

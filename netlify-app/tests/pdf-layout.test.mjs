import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pageText, streamText } from '../src/pdf-layout.mjs';
const word = (str, x, y, width = 40) => ({ str, transform: [1, 0, 0, 1, x, y], width });
test('reconstruye tabla de horarios aunque las columnas vengan desordenadas', () => {
  assert.equal(pageText([word('Martes', 180, 700), word('10:00', 300, 680), word('MT5307', 20, 680), word('Curso', 20, 700), word('Lunes', 180, 680)]), 'Curso | Martes\nMT5307 | Lunes | 10:00');
});
test('conserva palabras de una linea y descarta elementos sin coordenadas', () => {
  assert.equal(pageText([word('Control', 20, 700), word('Inteligente', 65, 701), { str: 'sin posicion' }, { type: 'markedContent' }]), 'Control Inteligente');
});

test('horario conserva el orden original: cada curso seguido de sus sesiones', () => {
  // Consolidado UTEC: las celdas estan centradas en altura, asi que la sesion del lunes de EL5102
  // queda dibujada mas arriba que su codigo. El orden del PDF la mantiene dentro de EL5102.
  const item = (str, y, hasEOL = true) => ({ str, transform: [1, 0, 0, 1, 10, y], width: 40, hasEOL });
  const items = [item('8 GI4101', 500), item('Semana General Lun.', 505), item('Teoría Virtual:19:00-22:00', 495),
    item('9 EL5102', 440), item('Semana General Lun.', 470), item('Teoría Virtual:18:00-19:00', 460),
    item('Semana General Jue.', 430), item('Laboratorio Virtual:09:00-12:00', 420)];
  const text = streamText(items);
  const gi = text.indexOf('GI4101'), el = text.indexOf('EL5102');
  assert.ok(gi < text.indexOf('19:00-22:00') && text.indexOf('19:00-22:00') < el);
  assert.ok(el < text.indexOf('18:00-19:00'));
  assert.ok(!text.includes('\n\n'));
});

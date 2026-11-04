import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analyze, importSchema, providerInfo } from '../server/ai.mjs';

const input = { kind: 'silabo', text: 'Texto academico de prueba con una evaluacion.' };
const data = { course: { code: 'MT', name: 'Control', credits: 4, difficulty: 3, color: '#235B4E', topics: [] }, evaluations: [{ name: 'PC1', type: 'PRACTICA', week: 4, weekday: 5, weight: 19.8 }], warnings: [] };
test('no intenta usar proveedores sin clave y nunca expone los valores de claves', async () => {
  await assert.rejects(analyze(input, {}, '2026-08-10', async () => { throw new Error('No debe llamar a la red'); }), e => e.status === 503);
  assert.deepEqual(providerInfo({ GROQ_API_KEY: 'secreto' }), { configured: ['groq'], selected: 'auto' });
});
test('Groq extrae JSON y convierte semanas al calendario del estudiante', async () => {
  const result = await analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async (url, options) => {
    assert.match(url, /^https:\/\/api.groq.com/); const sent = JSON.parse(options.body);
    assert.match(sent.messages[0].content, /no confiable/);
    return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }] });
  });
  assert.equal(result.evaluations[0].date, '2026-09-04'); assert.equal(result.evaluations[0].weight, 19.8);
});
test('auto puede pasar a Gemini cuando Groq responde cuota agotada', async () => {
  let calls = 0;
  const result = await analyze(input, { GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async url => {
    calls++;
    if (url.includes('groq.com')) return new Response('', { status: 429 });
    return Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify(data) }] } }] });
  });
  assert.equal(result.provider, 'gemini'); assert.equal(calls, 2);
});
test('un proveedor fijo no cambia a otro sin configuracion auto', async () => {
  let calls = 0;
  await assert.rejects(analyze(input, { IA_PROVEEDOR: 'groq', GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return new Response('', { status: 429 }); }), e => e.status === 502);
  assert.equal(calls, 1);
});
test('rechaza datos invalidos devueltos por IA en lugar de guardarlos', async () => {
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => Response.json({ choices: [{ message: { content: '{"course":{"name":"incompleto"}}' } }] })), e => e.status === 502);
  assert.equal(importSchema.safeParse({ kind: 'silabo', text: 'x' }).success, false);
  assert.equal(importSchema.safeParse({ kind: 'horario', image: 'https://url-arbitraria.example' }).success, false);
});

test('informa errores HTTP sin filtrar claves ni mensajes privados del proveedor', async () => {
  for (const [code, expected] of [[401, /clave rechazada/], [403, /acceso denegado/], [404, /modelo o recurso/], [429, /cuota o limite/], [400, /incompatible/], [503, /temporalmente/]]) {
    await assert.rejects(analyze(input, { GROQ_API_KEY: 'clave-privada' }, '2026-08-10', async () => Response.json({ error: 'documento y clave-privada' }, { status: code })), error => {
      assert.equal(error.status, 502); assert.match(error.message, expected);
      assert.ok(!error.message.includes('clave-privada')); assert.ok(!error.message.includes('documento y'));
      return true;
    });
  }
});

test('un timeout de proveedor permite intentar el siguiente y normaliza auto', async () => {
  let calls = 0;
  const result = await analyze(input, { IA_PROVEEDOR: ' AUTO ', GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async (url, options) => {
    assert.ok(options.signal instanceof AbortSignal); calls++;
    if (url.includes('groq.com')) throw new DOMException('dato privado', 'TimeoutError');
    return Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify(data) }] } }] });
  });
  assert.equal(result.provider, 'gemini'); assert.equal(calls, 2);
});

test('imagen omite Groq sin modelo de vision aunque haya otros proveedores', async () => {
  const timetable = { courses: [data.course], classes: [], warnings: [] };
  const result = await analyze({ kind: 'horario', text: '', image: 'data:image/jpeg;base64,YQ==' }, { GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async url => {
    assert.ok(!url.includes('groq.com'));
    return Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify(timetable) }] } }] });
  });
  assert.equal(result.provider, 'gemini');
});

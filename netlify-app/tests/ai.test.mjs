import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analyze, importSchema, providerInfo, normalizeExtraction, extractionJsonSchema } from '../server/ai.mjs';

const input = { kind: 'silabo', text: 'Texto academico de prueba con una evaluacion.' };
const data = { course: { code: 'MT', name: 'Control', credits: 4, difficulty: 3, color: '#235B4E', topics: [] }, evaluations: [{ name: 'PC1', type: 'PRACTICA', week: 4, weekday: 5, weight: 19.8 }], warnings: [] };

test('Groq compatible recibe esquema estricto cerrado y todas las propiedades requeridas', async () => {
  const visit = node => { if (node.type === 'object') { assert.equal(node.additionalProperties, false); assert.deepEqual(node.required.sort(), Object.keys(node.properties).sort()); Object.values(node.properties).forEach(visit); } if (node.items) visit(node.items); node.anyOf?.forEach(visit); };
  visit(extractionJsonSchema('silabo')); visit(extractionJsonSchema('horario'));
  await analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async (_, options) => { const sent = JSON.parse(options.body); assert.equal(sent.response_format.type, 'json_schema'); assert.equal(sent.response_format.json_schema.strict, true); assert.equal(sent.reasoning_effort, 'low'); return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }] }); });
});

test('modelos personalizados conservan JSON mode sin exigir soporte de esquema estricto', async () => {
  await analyze(input, { GROQ_API_KEY: 'test', GROQ_MODEL: 'modelo-personalizado' }, '2026-08-10', async (_, options) => { assert.equal(JSON.parse(options.body).response_format.type, 'json_object'); return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }] }); });
});

test('silabo reintenta una sola vez en JSON mode si Groq rechaza el esquema con 400', async () => {
  const formats = [];
  const result = await analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async (_, options) => {
    formats.push(JSON.parse(options.body).response_format.type);
    if (formats.length === 1) return Response.json({ error: { code: 'json_validate_failed', message: 'Failed to generate JSON' } }, { status: 400 });
    return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }] });
  });
  assert.deepEqual(formats, ['json_schema', 'json_object']); assert.equal(result.course.code, 'MT');
});

test('el respaldo de silabos conserva validacion y tiene un maximo de dos solicitudes', async () => {
  let calls = 0;
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return Response.json({ error: {} }, { status: 400 }); }), e => e.status === 502);
  assert.equal(calls, 2);
});

test('contexto demasiado largo no se reintenta con el mismo texto', async () => {
  let calls = 0;
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return Response.json({ error: { message: 'maximum context length exceeded' } }, { status: 400 }); }), e => /divide el silabo/.test(e.message));
  assert.equal(calls, 1);
});

test('el cambio de compatibilidad no reintenta horarios con error 400', async () => {
  let calls = 0;
  await assert.rejects(analyze({ ...input, kind: 'horario' }, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return new Response('', { status: 400 }); }), e => e.status === 502);
  assert.equal(calls, 1);
});

test('un horario subido como silabo muestra instrucciones y no inventa un curso', async () => {
  let calls = 0;
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return Response.json({ choices: [{ message: { content: JSON.stringify({ documentType: 'horario', course: null, evaluations: [], warnings: [] }) } }] }); }), e => e.status === 422 && /Mi horario/.test(e.message));
  assert.equal(calls, 1);
});

test('reconoce nombres en espanol sin convertir una lista de cursos en un silabo', () => {
  const result = normalizeExtraction({ curso: { codigo: 'MT', nombre: 'Control', creditos: '4', dificultad: '3', temas: [] }, evaluaciones: [] }, 'silabo');
  assert.equal(result.course.name, 'Control'); assert.equal(result.course.credits, 4);
  assert.equal(normalizeExtraction({ courses: [data.course] }, 'silabo').course, undefined);
});

test('una respuesta cortada no se acepta aunque contenga JSON parseable', async () => {
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => Response.json({ choices: [{ finish_reason: 'length', message: { content: JSON.stringify(data) } }] })), e => /respuesta incompleta/.test(e.message));
});

test('horario admite perfil ausente con advertencias y formatos equivalentes', async () => {
  const extracted = { courses: [{ code: 'mt', name: 'Control', credits: null, difficulty: null, color: null, topics: null }], classes: [{ code: ' mt ', weekday: '1', start: '8:00', end: '10:00:00', frequency: 'SEMANA_A', detail: null }] };
  const result = await analyze({ kind: 'horario', text: input.text }, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => Response.json({ choices: [{ message: { content: JSON.stringify(extracted) } }] }));
  assert.equal(result.courses[0].credits, 3); assert.equal(result.courses[0].code, 'MT');
  assert.equal(result.classes[0].code, 'MT'); assert.equal(result.classes[0].start, '08:00');
  assert.equal(result.classes[0].end, '10:00'); assert.equal(result.classes[0].frequency, 'A');
  assert.equal(result.warnings.length, 2); assert.equal(extracted.courses[0].credits, null);
});

test('no inventa el dia, no acepta horas invalidas ni referencias ajenas', async () => {
  for (const entry of [{ code: 'MT', start: '08:00', end: '10:00' }, { code: 'MT', weekday: 1, start: '25:00', end: '26:00' }, { code: 'OTRO', weekday: 1, start: '08:00', end: '10:00' }]) {
    await assert.rejects(analyze({ kind: 'horario', text: input.text }, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => Response.json({ choices: [{ message: { content: JSON.stringify({ courses: [data.course], classes: [entry] }) } }] })), error => error.status === 502);
  }
  assert.equal(normalizeExtraction({ courses: [{ credits: '4,5', difficulty: '3' }] }, 'horario').courses[0].credits, 4.5);
});
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

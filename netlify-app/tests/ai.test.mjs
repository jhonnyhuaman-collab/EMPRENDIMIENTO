import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analyze, importSchema, providerInfo, normalizeExtraction, extractionJsonSchema, compactSyllabus } from '../server/ai.mjs';

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
  // Solo avisa los creditos: un horario nunca trae dificultad (Render usa 3 sin avisar).
  assert.equal(result.warnings.length, 1); assert.match(result.warnings[0], /Creditos/); assert.equal(extracted.courses[0].credits, null);
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
  await assert.rejects(analyze(input, { IA_PROVEEDOR: 'groq', GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async () => { calls++; return new Response('', { status: 429 }); }), e => e.status === 429 && e.providerRateLimited);
  assert.equal(calls, 1);
});
test('rechaza datos invalidos devueltos por IA en lugar de guardarlos', async () => {
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => Response.json({ choices: [{ message: { content: '{"course":{"name":"incompleto"}}' } }] })), e => e.status === 502);
  assert.equal(importSchema.safeParse({ kind: 'silabo', text: 'x' }).success, false);
  assert.equal(importSchema.safeParse({ kind: 'horario', image: 'https://url-arbitraria.example' }).success, false);
});

test('informa errores HTTP sin filtrar claves ni mensajes privados del proveedor', async () => {
  for (const [code, expected] of [[401, /clave rechazada/], [403, /acceso denegado/], [404, /modelo o recurso/], [400, /incompatible/], [503, /temporalmente/]]) {
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

test('imagen de horario usa Groq con el modelo de vision de Render por defecto', async () => {
  const timetable = { courses: [data.course], classes: [], warnings: [] };
  const result = await analyze({ kind: 'horario', text: '', image: 'data:image/jpeg;base64,YQ==' }, { GROQ_API_KEY: 'test', GEMINI_API_KEY: 'test' }, '2026-08-10', async (url, options) => {
    assert.match(url, /groq\.com/);
    const sent = JSON.parse(options.body);
    assert.equal(sent.model, 'qwen/qwen3.8-27b');
    assert.match(sent.messages[1].content[0].text, /cursos matriculados y su horario semanal/);
    return Response.json({ choices: [{ message: { content: JSON.stringify(timetable) } }] });
  });
  assert.equal(result.provider, 'groq');
});

test('silabo envia la fecha de inicio del ciclo y las reglas de Render', async () => {
  await analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async (_, options) => {
    const sent = JSON.parse(options.body);
    assert.match(sent.messages[0].content, /EC1, EC2, EC3/);
    assert.match(sent.messages[0].content, /la app ubica cada evaluacion en la clase del alumno/);
    assert.match(sent.messages[1].content, /^Fecha de inicio del ciclo \(lunes de la semana 1\): 2026-08-10/);
    assert.match(sent.messages[1].content, /<silabo>\nTexto academico/);
    return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }] });
  });
});

test('peso real se calcula como en Render: componente x peso interno', () => {
  const raw = structuredClone(data);
  raw.evaluations = [
    { ...raw.evaluations[0], weight: 20, componentWeight: 60, weightInComponent: 33 },
    { ...raw.evaluations[0], name: 'Final', weight: null, componentWeight: '100', weightInComponent: '25' }
  ];
  const out = normalizeExtraction(raw, 'silabo');
  assert.equal(out.evaluations[0].weight, 19.8);
  assert.equal(out.evaluations[1].weight, 25);
});

test('limite por minuto de Groq devuelve 429 con el tiempo de espera del proveedor', async () => {
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'clave-privada' }, '2026-08-10', async () => new Response('{"error":{"message":"Rate limit reached"}}', { status: 429, headers: { 'retry-after': '23' } })), error => {
    assert.equal(error.status, 429); assert.equal(error.retryAfter, 23); assert.equal(error.providerRateLimited, true);
    assert.match(error.message, /limite por minuto/); assert.ok(!error.message.includes('clave-privada'));
    return true;
  });
});

test('documento demasiado grande para Groq no se reintenta como limite por minuto', async () => {
  await assert.rejects(analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async () => new Response('{"error":{"message":"Request too large for model on tokens per minute (TPM): Limit 8000, Requested 12000"}}', { status: 429 })), error => {
    assert.equal(error.status, 502); assert.ok(!error.providerRateLimited);
    assert.match(error.message, /demasiado grande/);
    return true;
  });
});

test('pesos: conserva el peso final de la IA si el producto por componentes no suma 100', () => {
  const raw = structuredClone(data);
  raw.evaluations = [20, 30, 50].map((w, i) => ({ ...raw.evaluations[0], name: `E${i}`, weight: w, componentWeight: 60, weightInComponent: w }));
  const out = normalizeExtraction(raw, 'silabo');
  assert.deepEqual(out.evaluations.map(e => e.weight), [20, 30, 50]);
  assert.ok(!out.warnings.some(w => /deben sumar 100/.test(w)));
});

test('pesos: usa componente x peso interno cuando el peso final de la IA no suma 100', () => {
  const raw = structuredClone(data);
  raw.evaluations = [
    ...[1, 2, 3].map(n => ({ ...raw.evaluations[0], name: `EC${n}`, weight: 33.33, componentWeight: 60, weightInComponent: 100 / 3 })),
    { ...raw.evaluations[0], name: 'Final', weight: 40, componentWeight: 100, weightInComponent: 40 }
  ];
  const out = normalizeExtraction(raw, 'silabo');
  assert.deepEqual(out.evaluations.map(e => e.weight), [20, 20, 20, 40]);
});

test('pesos: avisa cuando el total no suma 100', () => {
  const raw = structuredClone(data);
  raw.evaluations = [{ ...raw.evaluations[0], weight: 30, componentWeight: 100, weightInComponent: 30 }];
  const out = normalizeExtraction(raw, 'silabo');
  assert.ok(out.warnings.some(w => /suman 30% y deben sumar 100%/.test(w)));
});

test('el esquema para la IA describe como calcular los pesos', () => {
  const ev = extractionJsonSchema('silabo').properties.evaluations.items.properties;
  assert.match(ev.componentWeight.description, /componente/);
  assert.match(ev.weight.description, /100/);
});

test('horario: la IA devuelve cada curso con sus sesiones y la app las aplana, como Render', async () => {
  const schema = extractionJsonSchema('horario');
  assert.ok(!schema.properties.classes);
  assert.ok(schema.properties.courses.items.required.includes('sessions'));
  assert.ok(!schema.properties.courses.items.properties.sessions.items.properties.code);
  const extracted = { documentType: 'horario', warnings: [], courses: [
    { code: 'GI4101', name: 'Evaluación Financiera de Proyectos', credits: 3, difficulty: null, color: '#235B4E', topics: [], sessions: [{ weekday: 1, start: '19:00', end: '22:00', frequency: 'SEMANAL', detail: 'Teoría · Virtual' }] },
    { code: 'EL5102', name: 'Proyecto Final de Ingeniería Electrónica I', credits: 4, difficulty: null, color: '#235B4E', topics: [], sessions: [{ weekday: 1, start: '18:00', end: '19:00', frequency: 'SEMANAL', detail: 'Teoría · Virtual' }, { weekday: 4, start: '09:00', end: '12:00', frequency: 'SEMANAL', detail: 'Laboratorio · Virtual 110' }] },
    { code: 'MT5307', name: 'Control Inteligente', credits: 4, difficulty: null, color: '#235B4E', topics: [], sessions: [{ weekday: 1, start: '16:00', end: '19:00', frequency: 'SEMANAL', detail: 'Teoría · M803' }, { weekday: 4, start: '18:00', end: '22:00', frequency: 'A', detail: 'Laboratorio · L413' }] }] };
  const result = await analyze({ kind: 'horario', text: input.text }, { GROQ_API_KEY: 'test' }, '2026-08-10', async (_, options) => {
    const sent = JSON.parse(options.body);
    assert.match(sent.messages[0].content, /Cada sesion pertenece al curso cuya fila la contiene/);
    return Response.json({ choices: [{ message: { content: JSON.stringify(extracted) } }] });
  });
  assert.equal(result.classes.length, 5);
  assert.deepEqual(result.classes.filter(c => c.code === 'GI4101').map(c => c.start), ['19:00']);
  assert.equal(result.classes.find(c => c.code === 'MT5307' && c.weekday === 4).frequency, 'A');
  assert.ok(result.courses.every(c => !('sessions' in c)));
  assert.equal(result.warnings.length, 0);
});

test('compacta el silabo: quita paginas, encabezados repetidos y bibliografia, conserva la evaluacion', () => {
  const page = n => `--- Pagina ${n} ---\nUTEC · Silabo MT5307 · 2026-2\nContenido de la pagina ${n}\n${n}`;
  const text = [page(1), page(2), page(3), 'Bibliografia', 'Ogata, K. (2010). Ingenieria de control moderna. Pearson.', 'Sistema de evaluacion', 'EC1 semana 4 20%'].join('\n');
  const out = compactSyllabus(text);
  assert.ok(!out.includes('--- Pagina'));
  assert.ok(!out.includes('UTEC · Silabo MT5307'));
  assert.ok(!out.includes('Ogata'));
  assert.match(out, /Sistema de evaluacion\nEC1 semana 4 20%/);
  assert.match(out, /Contenido de la pagina 3/);
});

test('silabo: pide menos tokens de respuesta a Groq e informa los tokens usados', async () => {
  const result = await analyze(input, { GROQ_API_KEY: 'test' }, '2026-08-10', async (_, options) => {
    assert.equal(JSON.parse(options.body).max_completion_tokens, 4000);
    return Response.json({ choices: [{ message: { content: JSON.stringify(data) } }], usage: { total_tokens: 5321 } });
  });
  assert.equal(result.tokens, 5321);
});

test('pesos: quita un componente contado junto con sus partes', () => {
  const raw = structuredClone(data);
  raw.evaluations = [
    { ...raw.evaluations[0], name: 'Evaluaciones continuas', week: 4, weight: 60 },
    ...[4, 6, 10].map((week, i) => ({ ...raw.evaluations[0], name: `EC${i + 1}`, week, weight: 20 })),
    { ...raw.evaluations[0], name: 'Examen final', type: 'EXAMEN', week: 16, weight: 40 }
  ];
  const out = normalizeExtraction(raw, 'silabo');
  assert.deepEqual(out.evaluations.map(e => e.name), ['EC1', 'EC2', 'EC3', 'Examen final']);
  assert.ok(out.warnings.some(w => /Se quito "Evaluaciones continuas"/.test(w)));
  assert.ok(!out.warnings.some(w => /deben sumar 100/.test(w)));
});

test('fechas: avisa semanas fuera del ciclo, semanas repetidas y examenes fuera de lo habitual', () => {
  const raw = structuredClone(data);
  raw.evaluations = [
    { ...raw.evaluations[0], name: 'Examen parcial', type: 'EXAMEN', week: 3, weight: 30 },
    { ...raw.evaluations[0], name: 'PC1', week: 3, weight: 10 }, { ...raw.evaluations[0], name: 'PC2', week: 3, weight: 10 },
    { ...raw.evaluations[0], name: 'PC2', week: 3, weight: 10 },
    { ...raw.evaluations[0], name: 'Examen final', type: 'EXAMEN', week: 18, weight: 50 }
  ];
  const out = normalizeExtraction(raw, 'silabo');
  assert.equal(out.evaluations.filter(e => e.name === 'PC2').length, 1);
  assert.ok(out.warnings.some(w => /Se quito "PC2"/.test(w)));
  assert.ok(out.warnings.some(w => /semana 18, fuera de las 16 semanas/.test(w)));
  assert.ok(out.warnings.some(w => /3 evaluaciones en la semana 3/.test(w)));
  assert.ok(out.warnings.some(w => /parciales suelen ser en la semana 8/.test(w)));
});

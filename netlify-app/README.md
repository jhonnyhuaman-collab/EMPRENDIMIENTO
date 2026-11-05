# Planificador Academico: Netlify + Neon

Version independiente en React y JavaScript/Node.js. La aplicacion Java de Render
no se elimina ni se modifica. La especificacion original del curso exige Java;
conservar esa version para la entrega si el docente mantiene ese requisito.

## Interfaz original recuperada de Render

Esta version utiliza las hojas `estilos.css`, `marca.css` y `pwa.css` originales,
el logo, la fotografia, Bootstrap y sus iconos. Se trasladaron a componentes las
plantillas de Hoy, Agenda (con formulario lateral), Cursos (evaluaciones y temas),
Horario (grilla semanal), Configuracion, acceso, guia de evaluacion y revision.
No hay que cambiar carpetas ni crear otro proyecto de Netlify: actualizar este.

Se conservaron el repaso por tipo/tema, simulacro, inicio recomendado, prioridad,
semanas A/B, importacion con revision y registro manual. Al reimportar horarios se
reemplazan las clases de los cursos incluidos, conservando evaluaciones. Al
reimportar silabos se reemplazan sus evaluaciones identificadas como SILABO y se
conservan los pendientes manuales. Las evaluaciones antiguas sin origen guardado
se conservan para no borrar registros de la primera version de Netlify.

La adaptacion usa funciones Node en Netlify; no ejecuta Spring Boot alli ni
depende de Render como proxy. Las cuentas y datos de Render no se han copiado
automaticamente: el procedimiento opcional de migracion esta mas abajo.

## Lo que incluye

El plan de estudio incluye ahora preparacion semestral por temas, repaso final
y un resumen de las 16 semanas en Calendario. Las semanas de cada tema pueden
ajustarse al revisar el silabo o editar el curso. Tras actualizar esta version,
pulsar Actualizar mi plan para regenerar los pendientes conservando completados.
Detalles y limites: [plan de 16 semanas](docs/plan-16-semanas.md).

- Registro, login y cierre de sesion, nombres derivados del correo y perfil editable.
- Cursos, temas, dificultad, colores, tareas, examenes, laboratorios y proyectos.
- Notas obtenidas y contribucion a la nota final por peso.
- Horario manual o importado, con semanas A/B y deteccion de cruces.
- Calendario mensual, semanal y diario, detalle de evaluacion y descarga .ics.
- Planificador con limite diario, carga de clases, limite por curso/semana,
  descansos, dias elegidos, hora de dormir y bloques completados conservados.
- Replanificacion al detectar bloques atrasados en una nueva fecha.
- Lectura de silabos y horarios con Groq, Gemini o Anthropic, revision editable
  antes de guardar y procesamiento secuencial de hasta cinco documentos.
- PWA instalable, logo premium y login translucido, sin cachear informacion privada.

## Ejecutar localmente

Requiere Node.js 24. Desde esta carpeta:

```powershell
npm ci
npm run dev
```

Abrir http://127.0.0.1:5174 . Sin `.env`, usa PostgreSQL embebido local de PGlite
en `.local/postgres`. Es una base real de prueba, no datos ficticios en memoria
del navegador. No consulta la base H2, Neon ni la aplicacion de Render.
El API local ocupa 5184. Las pruebas automaticas usan otra base temporal.

La IA requiere al menos una clave en `.env`; el registro manual no la necesita.
Las cuentas locales no aparecen automaticamente en la nube.

## Preparar Neon sin tocar la version anterior

1. Crear otra base de datos en tu proyecto de Neon para esta version (o un proyecto
   de prueba). No borrar ni reemplazar la base existente de Java.
2. Copiar `.env.example` a `.env` y completar `DATABASE_URL` con la cadena de Neon:
   `postgresql://USUARIO:CLAVE@HOST/BASE_NUEVA?sslmode=require`.
   No usar el prefijo `jdbc:` de Java.
3. Generar `SESSION_SECRET` con este comando y colocar su resultado en `.env`:

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

4. Preparar solo las tablas `pa_netlify_*` en la base nueva:

```powershell
$env:CONFIRM_NEW_DATABASE = "YES"
npm run db:prepare
$env:CONFIRM_NEW_DATABASE = $null
```

Las funciones no crean tablas ni migran esquemas al recibir visitas.
La base queda vacia hasta registrar cuentas o ejecutar la copia opcional de Java.

## Publicar en Netlify

Subir la carpeta `netlify-app` al repositorio GitHub que contiene la version Java.
En Netlify: agregar un proyecto desde ese repositorio y configurar:

| Ajuste | Valor |
| --- | --- |
| Base directory | `netlify-app` |
| Build command | `npm run build` |
| Publish directory | `dist` (relativa a Base directory) |
| Functions directory | `netlify/functions` (relativa a Base directory) |
| Version de Node | `24` |

El `netlify.toml` de esta carpeta contiene las rutas, headers y opciones de funciones.
Si publicas esta carpeta como un repositorio independiente, Base directory debe
quedar vacia. No arrastrar solo `dist` para publicarla: eso no despliega las funciones.

Agregar las variables en Netlify, no en el codigo ni en variables `VITE_`:

| Variable | Obligatoria |
| --- | --- |
| `DATABASE_URL` | Si, la URL de la nueva base Neon |
| `SESSION_SECRET` | Si, secreto aleatorio largo |
| `IA_PROVEEDOR` | Opcional: `auto`, `groq`, `gemini` o `anthropic` |
| `GROQ_API_KEY`, `GEMINI_API_KEY` o `ANTHROPIC_API_KEY` | Una para utilizar IA |
| `GROQ_MODEL` | Opcional, por defecto `openai/gpt-oss-120b` |
| `GROQ_VISION_MODEL` | Necesaria para leer imagenes con Groq; elegir un modelo vigente con vision |
| `GEMINI_MODEL` | Opcional, por defecto `gemini-3.8-flash` |
| `ANTHROPIC_MODEL` | Opcional, por defecto `claude-sonnet-5` |

En `auto`, intenta los proveedores con claves configuradas en orden Groq, Gemini,
Anthropic, con un plazo total de 45 segundos. Una cuota o modelo agotado puede
impedir la lectura. No cambia tu plan de pago de IA ni compra recursos.
Guardar variables y desplegar nuevamente. `/api/health` debe responder `UP`.
Verificar registro, login, curso, tarea, horario, configuracion, PDF y exportacion
antes de cambiar enlaces o dejar de usar Render.

## Copiar cuentas y datos de la version Java (opcional)

No se ha ejecutado una migracion contra tus bases reales. El script primero lee
el origen y verifica los datos; de forma predeterminada no escribe en el destino.
Origen y destino deben ser bases distintas. Usar credenciales de solo lectura
para el origen si es posible y hacer una copia de respaldo en Neon antes de empezar.

Agregar temporalmente a `.env` `JAVA_DATABASE_URL` con la URL PostgreSQL de la
base de Java (sin `jdbc:`). Preparar primero las tablas del destino.

```powershell
node --env-file=.env scripts/import-java.mjs
```

Si la revision termina correctamente, detener las escrituras en la version Java
durante la copia final para no perder cambios realizados despues de leer el origen:

```powershell
$env:CONFIRM_IMPORT = "YES"
node --env-file=.env scripts/import-java.mjs
$env:CONFIRM_IMPORT = $null
```

Copia cuentas registradas, hashes BCrypt, preferencias, cursos, temas, evaluaciones,
notas, horarios y bloques. No copia sesiones: hay que iniciar sesion otra vez.
Los usuarios existentes en el destino se omiten, nunca se sobrescriben.
Una cuenta inicial sin clave se omite porque no tiene credenciales de acceso.
Cada cuenta se copia atomicamente; si hay un fallo, se puede revisar y repetir
sin reemplazar las que ya se copiaron. No borra ni escribe en el origen.
Revisar conteos y cuentas de prueba antes del cambio final. La H2 local no se
importa con este script: este procedimiento es para el PostgreSQL de Neon.

## Verificar

```powershell
npm test
npm run build
npm run check:functions
```

Las pruebas cubren sesiones persistentes, aislamiento, CSRF, cookies, limites de
intentos, concurrencia, validaciones, horarios, planificador, ICS e importacion.
La IA se simula en pruebas: no consumen tus claves ni tus cuotas.
La conexion real a Neon y una lectura real con IA requieren configurar tus variables.

## Seguridad y limites

- Cookies HttpOnly, Secure en produccion y SameSite; sesiones revocables de siete
  dias en PostgreSQL. No se guardan tokens ni contrasenas en localStorage.
- Consultas parametrizadas y todo acceso se vincula al usuario autenticado.
- Concurrencia optimista: una segunda ventana no sobrescribe cambios silenciosamente.
- Las claves y conexiones viven solo en el servidor. `.env` y `.local` estan ignorados.
- El PDF se lee en el dispositivo con PDF.js y se envia solo el texto al API/IA.
  Limite de 15 MB, 150 paginas y 150.000 caracteres; no incluye OCR de PDF escaneado.
  Las imagenes se reducen antes de enviarlas; el API limita el cuerpo a 4,3 MB.
- La lectura con IA no guarda nada hasta que confirmas la revision.
- La PWA requiere internet para las cuentas, los datos y la IA. Solo se cachean
  la pantalla sin conexion y sus recursos publicos, nunca documentos ni sesiones.
- No incluye verificacion de correo, recuperacion de clave por correo ni push.
- Netlify, Neon y los proveedores de IA tienen cuotas propias. Esta migracion no
  garantiza disponibilidad continua, uso ilimitado ni elimina todos los arranques en frio.

Referencias: [Netlify Functions](https://docs.netlify.com/build/functions/overview/),
[variables del servidor](https://docs.netlify.com/build/functions/environment-variables/),
[driver de Neon](https://github.com/neondatabase/serverless).

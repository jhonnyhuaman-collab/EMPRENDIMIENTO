# Planificador Académico

Aplicación web que convierte los sílabos de tus cursos en un plan de estudio diario.
Proyecto del curso de Emprendimiento – UTEC (Lima). Especificación completa en [SPEC.md](SPEC.md).

## Requisitos
- JDK 17 o superior (probado con JDK 21)
- Maven 3.9+
- Conexión a internet la primera vez (Maven descarga dependencias; Bootstrap y FullCalendar se cargan por CDN)

## Ejecutar

```bash
mvn spring-boot:run
```

Luego abre <http://localhost:8081>.

Pulsa **Crear cuenta** y registra tu correo y una contraseña. La opcion permanece disponible en el login.
El nombre inicial se obtiene del correo (por ejemplo, `jhonny.perez@utec.edu.pe` → `Jhonny Perez`)
y se puede editar en **Configuración → Mi perfil**. La primera cuenta conserva los cursos y pendientes del perfil inicial;
las siguientes tienen sus propios cursos, agenda, horario y configuración, y empiezan vacías.
El correo no se verifica y no es un acceso con Google. Se requiere iniciar sesión para acceder al plan.
Las contraseñas se guardan con BCrypt y cada correo se puede registrar una sola vez.
La agenda muestra la semana académica junto a cada fecha, calculada desde el inicio del ciclo configurado.

También puedes generar el `.jar` y ejecutarlo:

```bash
mvn clean package
java -jar target/planificador-academico-0.1.0.jar
```

> El puerto por defecto es **8081** (el 8080 está ocupado en la PC de desarrollo). Para usar otro: `$env:PORT = "9090"` antes de ejecutar.

## Cómo funciona el plan de estudio (Fase 2)
- Cada evaluación tiene horas estimadas (según tipo, dificultad del curso y peso).
- Se empieza a preparar entre 3 y 14 días antes, según cuántas horas necesita.
- Cada día se reparte lo que falta en partes iguales entre los días restantes, con un tope diario
  por evaluación (3 h exámenes y proyectos, 2 h el resto): **nunca todo el último día**.
- Si varias evaluaciones compiten por el mismo día, primero cada una avanza un poco (hasta 1 h)
  y luego el resto se asigna por **prioridad = cercanía (50%) + peso en la nota (30%) + dificultad (20%)**.
- Los bloques duran hasta 90 min, con 15 min de descanso; no chocan con clases y terminan 1 h antes de `horaDormir`.
- **Carga diaria sana:** máximo `horasEstudioPorDia` (por defecto 3 h) y sin que clases + estudio pasen la
  **carga máxima diaria** (por defecto 9 h): un día con 6 h de clase recibe como máximo 3 h de estudio.
- **Solo de lunes a viernes** por defecto (fin de semana libre); se puede activar el sábado u otros días en Configuración.
- Se prefiere estudiar **después de la última clase del día**.
- Las entregas de los próximos 3 días y sus bloques de estudio se muestran **en rojo**.
- El botón **Actualizar mi plan** recalcula todo desde hoy y conserva los bloques completados.
- Si no alcanza el tiempo, la app muestra una advertencia con las horas que faltan.

## Configuración del ciclo
- **Configuración** (menú lateral o ícono ⚙ en el celular): fecha de inicio del ciclo (lunes de la semana 1),
  horas de estudio por día y hora de dormir.
- Semana N = inicio + (N − 1) × 7 días. Semana A = impares, Semana B = pares.
- Al cambiar el inicio, cada evaluación conserva su semana y su día, se recalcula su fecha y se rehace el plan.
- Valor por defecto: lunes 10/08/2026 (ciclo 2026-2).

## Publicar en Render con PostgreSQL

El proyecto incluye `Dockerfile`, `render.yaml` y un perfil `prod` para usar
PostgreSQL de Neon en Render Free, sin subir la base local ni las claves de IA.
La nube empieza sin cursos ni cuentas registradas. Sigue la
[guía de despliegue](docs/despliegue-render.md) para configurar las credenciales
y crear el servicio. El chequeo público `/actuator/health` solo muestra el estado.

## Base de datos local
- H2 en archivo, en la carpeta `./data/` (se crea sola).
- La app empieza **sin cursos**: subes tus sílabos desde **Subir sílabo** (o los registras a mano).
- Cada curso se puede eliminar desde **Mis cursos** (ícono de papelera); el plan se recalcula.
- Para **reiniciar todo**, detén la app y borra la carpeta `data/`.
- Cursos de ejemplo (MT5307 y 2 ficticios): solo si pones `planificador.datos-ejemplo=true` y la base está vacía.
  Las pruebas automáticas los usan.
- La consola H2 está bloqueada desde la aplicación para mantener separados los datos de las cuentas.

## IA para leer sílabos (desde la Fase 3): Claude o Gemini
Las API keys **nunca** van en el código ni en `application.properties`; se leen de variables de entorno.
Basta con **una**:

| Proveedor | Variable de entorno | Dónde se crea la clave |
|-----------|---------------------|------------------------|
| Gemini (Google) | `GEMINI_API_KEY` | <https://aistudio.google.com/apikey> |
| Groq | `GROQ_API_KEY` | <https://console.groq.com/keys> (empieza con `gsk_`) |
| Claude (Anthropic) | `ANTHROPIC_API_KEY` | <https://console.anthropic.com> (empieza con `sk-ant-`) |

PowerShell (permanente; luego **cierra y vuelve a abrir VS Code**):
```powershell
setx GEMINI_API_KEY "tu-api-key"
```

En `application.properties`:
```properties
planificador.ia.proveedor=auto          # auto | gemini | groq | anthropic
planificador.ia.orden=gemini, groq, anthropic   # en "auto": el primero con clave; si falla algo pasajero, el siguiente
planificador.ia.anthropic.modelo=claude-sonnet-5
planificador.ia.gemini.modelo=gemini-3.8-flash
planificador.ia.gemini.modelos-respaldo=gemini-3.7-flash, gemini-flash-latest
```
La pantalla **Subir sílabo** muestra qué proveedor y modelo se están usando, y cuáles quedan de respaldo.

**Groq:** texto con `openai/gpt-oss-120b` y fotos de horario con `qwen/qwen3.8-27b` (el único con visión).
Groq no lee PDF: de un PDF de matrícula se envía el texto extraído con PDFBox. En el plan gratuito los límites
son bajos (~8000 tokens/min en gpt-oss y ~1000 tokens de salida/min en Qwen); si un sílabo es muy grande,
la app pasa automáticamente al siguiente proveedor.

### Cómo funciona la subida de sílabos (Fase 3)
1. **Subir sílabo** → eliges el PDF. Apache PDFBox extrae el texto (si el PDF es un escaneo sin texto, la app avisa).
2. El texto se envía a la IA (Claude o Gemini) con **salida estructurada**: la respuesta llega como
   objeto Java tipado (`SilaboExtraidoDto`).
3. La IA devuelve, por cada evaluación, el peso del componente y el peso dentro del componente;
   **la app** calcula el peso real (60% × 33% = 19.8%) y estima las horas.
4. Revisas todo en una tabla editable (agregar/quitar filas, total de pesos en vivo) y guardas.
   Si el curso ya existía (mismo código), se reemplaza. El plan de estudio se regenera.
- Sin API key, la opción **Registrar manualmente** usa la misma tabla sin IA.
- Ambos proveedores usan las mismas instrucciones y el mismo esquema de salida; Claude con el SDK oficial
  y salida estructurada, Gemini con `generateContent` + `responseJsonSchema`.

### Mi horario: importar desde foto o consolidado de matrícula
1. **Mi horario** → **Importar horario** → sube una captura/foto (JPG, PNG, WEBP) o el PDF de tu matrícula.
2. La IA (Claude o Gemini) lee cada curso con sus clases: día, hora, tipo y aula.
3. Revisas la tabla: puedes cambiar el **color** de cada curso, corregir horas o agregar/quitar clases.
4. Al guardar, los cursos nuevos se crean y los que ya existían conservan sus evaluaciones (solo cambia su horario).
   Verás tu semana en una grilla, **un color por curso**, y el plan de estudio evita esas horas.
- **Clases quincenales:** "Semana A" = semanas impares (1, 3, 5…) y "Semana B" = semanas pares (2, 4, 6…).
  La IA las detecta; en Mi horario puedes ver Semana A o B, y el calendario y el plan solo las cuentan en su semana.
- Luego sube el sílabo de cada curso: se reconoce el curso (por código o por nombre), se conservan su color y su
  horario, y cada evaluación cae en un día de clase: laboratorios el día de laboratorio (de esa semana A/B),
  exámenes y prácticas el día de teoría.
- Si Gemini está saturado (error 503), la app reintenta y prueba los modelos de `planificador.ia.gemini.modelos-respaldo`.

### Agenda: lo que no está en el sílabo
- **Agenda** (menú): anota tareas, quizzes o evaluaciones que dejan en clase: curso, tipo, fecha, hora límite,
  peso (opcional), horas de trabajo (opcional; si no, se estiman) y notas.
- Se guardan como evaluaciones del curso: aparecen en Hoy, el calendario y el plan de estudio.
- **Marcar como entregado** (check) hace que el plan deje de programarle estudio. También se puede editar o eliminar.
- Desde el detalle de cualquier evaluación (también las del sílabo) puedes **cambiar su fecha** si el profesor la movió.
- Si vuelves a subir el sílabo de un curso, sus pendientes de agenda se conservan.

### Replanificación y Google Calendar (Fase 4)
- **Check de bloques**: marca cada bloque al terminarlo (Hoy o Detalle de evaluación).
- **Replanificación automática**: al abrir una pantalla, si quedaron bloques de días pasados sin marcar,
  se marcan como *reprogramados* y sus horas se reparten desde hoy (respetando las mismas reglas del plan).
  Aparece un aviso dorado con el resumen. Si luego marcas un bloque reprogramado como hecho, el plan se recalcula.
- **Exportar**: Calendario → **Exportar a Google Calendar** descarga `plan-de-estudio.ics`
  (evaluaciones como eventos de día completo y bloques de estudio con recordatorio de 10 min).
  En Google Calendar: Configuración → Importar y exportar → Importar. Reimportar actualiza los eventos (UID estables).

## Pantallas
| Ruta                  | Pantalla                                   |
|-----------------------|--------------------------------------------|
| `/`                   | Panel "Hoy"                                |
| `/calendario`         | Calendario unificado (FullCalendar)        |
| `/cursos`             | Mis cursos                                 |
| `/evaluaciones/{id}`  | Detalle de evaluación                      |
| `/horario`            | Mi horario (grilla semanal por colores)    |
| `/silabos/subir`      | Subir sílabo (PDF + IA)                    |
| `/silabos/manual`     | Registrar un curso a mano                  |
| `/plan/exportar.ics`  | Descarga del plan para Google Calendar     |
| `/agenda`             | Agenda: tareas y evaluaciones extra        |
| `/configuracion`      | Inicio del ciclo y ritmo de estudio        |

## Estructura
```
src/main/java/pe/utec/planificador
├── model/        entidades JPA
├── repository/   repositorios Spring Data
├── service/      lógica de negocio
├── controller/   controladores web
├── dto/          objetos de transferencia
└── config/       configuración y DataLoader
src/main/resources
├── templates/    vistas Thymeleaf (+ fragments/layout.html)
└── static/       css y js propios
```

## Estado por fases
- [x] Fase 1 – Estructura, entidades, datos de ejemplo y pantallas Hoy, Calendario, Mis cursos, Detalle
- [x] Fase 2 – Generador del plan de estudio (`PlanificadorService`) + rediseño premium
- [x] Fase 3 – Subir sílabo (PDFBox + IA) con tabla editable
- [x] Fase 4 – Replanificación automática y exportación `.ics`

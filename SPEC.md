# SPEC – Planificador Académico

Proyecto del curso de Emprendimiento (UTEC, Lima). Toda la interfaz está en español.

## 1. Contexto del problema
Validamos con 12 entrevistas que los estudiantes universitarios conocen las fechas
de sus evaluaciones, pero no logran convertirlas en un plan semanal: no saben
cuánto trabajo implica cada entrega, priorizan solo por fecha, empiezan tarde y
la información está dispersa (Canvas, sílabos, Google Calendar, WhatsApp, notas).

Frase clave de un usuario: *"El calendario me dice la fecha, pero no sé cuánto
trabajo implica cada entrega"*.

## 2. La solución
Una app web donde el estudiante sube los sílabos (PDF) de sus cursos y la app:
1. Extrae con IA las evaluaciones, semanas, pesos y temas.
2. Muestra un calendario unificado de todos los cursos.
3. Genera un plan de estudio diario: qué estudiar, cuándo empezar y cuánto tiempo.
4. Muestra una guía de temas por evaluación.
5. Replanifica si el estudiante se atrasa.

## 3. Stack tecnológico (obligatorio)
- Java 17+ con Spring Boot 3 y Maven
- Thymeleaf (vistas HTML) + Bootstrap 5 (diseño responsive, que funcione en celular)
- Spring Data JPA con base de datos H2 (en archivo) para el MVP
- Apache PDFBox para leer el texto de los sílabos
- SDK oficial de Anthropic para Java (`com.anthropic:anthropic-java`) para la IA,
  modelo configurable en `application.properties` (por defecto `claude-sonnet-5`)
- *(Agregado)* Alternativa: Google Gemini vía API REST `generateContent` (modelo por defecto
  `gemini-3.8-flash`). `planificador.ia.proveedor=auto|anthropic|gemini` elige cuál usar.
- FullCalendar (por CDN) para el calendario
- Lombok para reducir código repetitivo

## 4. Arquitectura
Paquete base: `pe.utec.planificador`

| Paquete / carpeta        | Contenido                                              |
|--------------------------|--------------------------------------------------------|
| `model/`                 | Entidades JPA                                          |
| `repository/`            | Interfaces `JpaRepository`                             |
| `service/`               | Lógica de negocio (una clase por responsabilidad)      |
| `controller/`            | Controladores web (`@Controller`)                      |
| `dto/`                   | Objetos para transferir datos (ej. resultado de la IA) |
| `config/`                | Configuración y datos de ejemplo (`DataLoader`)        |
| `resources/templates/`   | Vistas Thymeleaf, con un layout común (fragments)      |
| `resources/static/`      | CSS y JS propios                                       |

## 5. Modelo de datos
- **Estudiante**: id, nombre, email, fechaInicioCiclo, horasEstudioPorDia, horaDormir
- **Curso**: id, codigo, nombre, creditos, dificultad (1-5), color, estudiante
- **Tema**: id, unidad, numero, titulo, curso
- **Evaluacion**: id, nombre, tipo (PRACTICA, EXAMEN, LABORATORIO, PROYECTO, TAREA),
  semana, fecha, pesoEnNotaFinal (%), horasEstimadas, nota (opcional), curso,
  lista de temas que evalúa
- **BloqueEstudio**: id, fecha, horaInicio, horaFin, descripcion, completado, evaluacion
- **HorarioClase**: id, diaSemana, horaInicio, horaFin, curso

## 6. Pantallas
1. **Panel "Hoy"** (página principal): bloques de estudio del día con check, entregas
   de la semana con su peso, y alerta de "semana crítica" si en los próximos
   14 días hay 3 o más evaluaciones.
2. **Calendario**: vista mensual/semanal con FullCalendar; un color por curso;
   muestra evaluaciones y bloques de estudio.
3. **Mis cursos**: tarjetas por curso con evaluaciones, peso en la nota final y temas.
4. **Detalle de evaluación**: datos, temas que entran, plan de repaso, checklist.
5. **Subir sílabo**: formulario para subir un PDF y una tabla editable para revisar
   lo extraído antes de guardar.

## 7. Reglas de negocio
- Las semanas del sílabo se convierten a fechas con `fechaInicioCiclo`
  (semana 1 = fecha de inicio).
- Peso real en nota final: si el sílabo dice "Teoría 80%: 3 EC 60% (EC1 33%)",
  entonces EC1 = 60% × 33% = 19.8% de la nota final.
- Prioridad = cercanía de la fecha + peso en la nota + dificultad del curso.
- El plan reparte las `horasEstimadas` de cada evaluación en bloques durante los
  días previos (nunca todo el último día), sin superar `horasEstudioPorDia` y sin
  chocar con `HorarioClase`.

## 8. Datos de ejemplo (DataLoader)
Un estudiante de prueba y el curso real **MT5307 – Control Inteligente**:
- Dificultad 4, fecha de inicio del ciclo: 10/08/2026 (corregida; se puede cambiar en Configuración)
- EC1 semana 6 (19.8%), EC2 semana 11 (19.8%), EC3 semana 15 (20.4%),
  Proyecto semana 16 (20%), Laboratorios L1-L7 en semanas 4, 6, 8, 10, 12, 14, 16
  (L1-L5: 2.8% cada uno, L6-L7: 3% cada uno)
- Unidades: 6.1 Introducción al control inteligente, 6.2 Sistemas difusos,
  6.3 Algoritmos evolutivos, 6.4 Redes neuronales, 6.5 Sistemas neuro-difusos
  (ANFIS), 6.6 Aprendizaje por refuerzo
- Temas por evaluación (estimados): EC1 = 6.1 y 6.2; EC2 = 6.3 y 6.4; EC3 = 6.5 y 6.6

Además, 2 cursos ficticios para que el calendario se vea con carga real:
- **MA3001 – Ecuaciones Diferenciales** (dificultad 4)
- **IN3020 – Gestión de Proyectos** (dificultad 2)

## 9. Forma de trabajo
- Construcción por fases, deteniéndose al final de cada una para probar:
  - **FASE 1**: estructura del proyecto, entidades, DataLoader y las pantallas
    1, 2, 3 y 4 con los datos de ejemplo (sin IA todavía).
  - **FASE 2**: generador del plan de estudio (servicio `PlanificadorService`).
  - **FASE 3**: subir sílabo + extracción con PDFBox + IA de Anthropic + tabla
    editable de revisión.
  - **FASE 4**: marcar bloques como completados y replanificación automática;
    exportar el plan como archivo `.ics` para Google Calendar.
- Código limpio: nombres en español, comentarios breves, una responsabilidad
  por clase, sin lógica de negocio en los controladores.
- La API key de Anthropic se lee de la variable de entorno `ANTHROPIC_API_KEY`
  (o la de Gemini de `GEMINI_API_KEY`).
  Nunca se escribe en el código ni en `application.properties`.
- `.gitignore` adecuado para Java/Maven.
- Al terminar cada fase se verifica que compile con `mvn clean package`.

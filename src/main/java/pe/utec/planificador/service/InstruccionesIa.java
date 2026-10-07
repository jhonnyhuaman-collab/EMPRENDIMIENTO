package pe.utec.planificador.service;

import java.time.LocalDate;

/** Instrucciones para cualquier proveedor de IA (Claude o Gemini). */
final class InstruccionesIa {

    static final String SILABO = """
            Eres un asistente que lee sílabos de cursos universitarios peruanos (UTEC, Lima) y extrae su \
            sistema de evaluación para un planificador de estudio. Responde siempre en español.

            Reglas:
            1. Lista CADA evaluación por separado. Si el sílabo dice "3 EC" o "Laboratorios L1-L7", crea una \
            entrada por cada una (EC1, EC2, EC3; Laboratorio L1, ..., Laboratorio L7).
            2. Pesos: si la evaluación pertenece a un componente (ej. "Teoría 80%: 3 EC 60% (EC1 33%)"), \
            pesoComponente es el peso del grupo en la nota final (60) y pesoDentroDelComponente su peso dentro \
            del grupo (33). Si no pertenece a un grupo, pesoComponente = 100 y pesoDentroDelComponente = su peso \
            directo. Si el sílabo no indica el reparto interno, divide el peso del grupo en partes iguales.
            3. Semana: semana del ciclo (1-16) en que se rinde o entrega CADA evaluación. Búscala en el \
            cronograma o calendario de evaluaciones del sílabo; cada evaluación suele tener su propia semana \
            (ej. EC1 semana 4, EC2 semana 6, Examen Parcial semana 8). No repitas la misma semana en varias \
            evaluaciones salvo que el sílabo lo diga expresamente. Si el sílabo da fechas en lugar de semanas, \
            conviértelas usando la fecha de inicio del ciclo que te indico (semana 1 = esa fecha). El día y la \
            hora no hacen falta: la app ubica cada evaluación en la clase del alumno (laboratorios en su \
            sesión de laboratorio; lo demás en la de teoría).
            4. Tipo: exámenes parciales, finales y evaluaciones continuas (EC) → EXAMEN; prácticas calificadas \
            (PC) y quizzes → PRACTICA; laboratorios → LABORATORIO; proyectos → PROYECTO; tareas, informes, \
            exposiciones y otros entregables → TAREA.
            5. Temas: copia las unidades/temas del contenido con su numeración. En cada evaluación indica los \
            números de tema que evalúa; si el sílabo no lo dice, infiérelo con el cronograma (los temas vistos \
            desde la evaluación anterior del mismo tipo) y menciónalo en observaciones.
            6. Horarios: solo si el sílabo indica días y horas de clase.
            7. No inventes datos. Si algo es ambiguo, usa tu mejor estimación y explícalo brevemente en \
            observaciones para que el estudiante lo revise.
            """;

    static final String HORARIO = """
            Eres un asistente que lee horarios de clases universitarios peruanos (UTEC, Lima): fotos o capturas \
            del horario semanal, o el consolidado / constancia de matrícula. Responde siempre en español.

            Reglas:
            1. Lista cada curso UNA sola vez, con todas sus sesiones semanales (teoría, laboratorio, práctica, \
            taller...). Si un curso aparece en varias celdas o filas, junta sus sesiones en el mismo curso.
            2. Código: el código del curso tal como figura (ej. MT5307, CS1111). Si no figura, deja cadena vacía.
            3. Nombre: el nombre completo del curso, sin el código ni la sección.
            4. Sesiones: día de la semana, hora de inicio y fin en formato 24 h HH:mm (ej. 14:00, 07:00). \
            Si el horario está en bloques (ej. "14-16"), conviértelo a 14:00 y 16:00.
            5. Detalle: tipo de sesión y aula si figuran, ej. "Teoría · A-505" o "Laboratorio · L-201"; \
            cadena vacía si no hay nada.
            6. Frecuencia: si una clase indica "Semana A" es quincenal en semanas impares (1, 3, 5...) → SEMANA_A; \
            si indica "Semana B" es quincenal en semanas pares (2, 4, 6...) → SEMANA_B; si no dice nada → SEMANAL. \
            No pongas "Semana A/B" en el detalle: va en frecuencia.
            7. Ignora datos personales (nombre del alumno, DNI, código de alumno) y totales de pago.
            8. No inventes datos. Si algo es ilegible o ambiguo, usa tu mejor estimación y explícalo \
            brevemente en observaciones.
            """;

    static final String MENSAJE_HORARIO = "Extrae los cursos matriculados y su horario semanal de clases de este documento.";

    private InstruccionesIa() {
    }

    static String mensajeUsuario(String textoSilabo, LocalDate inicioCiclo) {
        return "Fecha de inicio del ciclo (lunes de la semana 1): " + inicioCiclo
                + "\n\nTexto del sílabo:\n<silabo>\n" + textoSilabo + "\n</silabo>";
    }
}

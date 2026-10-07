package pe.utec.planificador.service;

import pe.utec.planificador.model.FrecuenciaClase;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON Schemas de la salida estructurada (Gemini y Groq). */
final class EsquemasJson {

    private EsquemasJson() {
    }

    static Map<String, Object> silabo() {
        Map<String, Object> tema = objeto(Map.of(
                "unidad", entero("Número de unidad (1, 2, 3...)"),
                "numero", texto("Numeración del tema tal como figura en el sílabo, ej. \"6.2\""),
                "titulo", texto("Título del tema")),
                List.of("unidad", "numero", "titulo"));

        Map<String, Object> evaluacion = objeto(Map.of(
                "nombre", texto("Nombre corto, ej. \"EC1\", \"Laboratorio L3\", \"Examen final\""),
                "tipo", enumerado(nombres(TipoEvaluacion.values())),
                "semana", entero("Semana del ciclo (1-16) en que se rinde o entrega"),
                "pesoComponente", numero("Peso en % del componente que la contiene sobre la nota final; 100 si no pertenece a un componente"),
                "pesoDentroDelComponente", numero("Peso en % de esta evaluación dentro de su componente; si no pertenece a uno, su peso directo"),
                "temas", lista(texto("Número de tema, ej. \"6.1\""), "Números de los temas que evalúa")),
                List.of("nombre", "tipo", "semana", "pesoComponente", "pesoDentroDelComponente", "temas"));

        Map<String, Object> horario = objeto(Map.of(
                "dia", enumerado(nombres(DayOfWeek.values())),
                "horaInicio", texto("Hora de inicio HH:mm (24 h)"),
                "horaFin", texto("Hora de fin HH:mm (24 h)")),
                List.of("dia", "horaInicio", "horaFin"));

        return objeto(Map.of(
                "codigo", texto("Código del curso, ej. MT5307"),
                "nombre", texto("Nombre del curso, sin el código"),
                "creditos", entero("Número de créditos; 0 si el sílabo no lo indica"),
                "dificultadEstimada", entero("Dificultad estimada de 1 (fácil) a 5 (muy difícil)"),
                "temas", lista(tema, "Temas o unidades del contenido, en orden"),
                "evaluaciones", lista(evaluacion, "Cada evaluación individual, nunca agrupadas"),
                "horarios", lista(horario, "Horario semanal de clases; lista vacía si no figura"),
                "observaciones", texto("Notas breves sobre supuestos o datos ambiguos para que el estudiante revise")),
                List.of("codigo", "nombre", "creditos", "dificultadEstimada", "temas", "evaluaciones", "horarios", "observaciones"));
    }

    /** Esquema de HorarioMatriculaDto. */
    static Map<String, Object> horario() {
        Map<String, Object> sesion = objeto(Map.of(
                "dia", enumerado(nombres(DayOfWeek.values())),
                "horaInicio", texto("Hora de inicio HH:mm (24 h)"),
                "horaFin", texto("Hora de fin HH:mm (24 h)"),
                "detalle", texto("Tipo de sesión y aula si figuran, ej. \"Teoría · A-505\"; cadena vacía si no hay"),
                "frecuencia", enumerado(nombres(FrecuenciaClase.values()))),
                List.of("dia", "horaInicio", "horaFin", "detalle", "frecuencia"));

        Map<String, Object> curso = objeto(Map.of(
                "codigo", texto("Código del curso; cadena vacía si no figura"),
                "nombre", texto("Nombre del curso"),
                "creditos", entero("Créditos; 0 si no figuran"),
                "sesiones", lista(sesion, "Sesiones semanales de clase del curso")),
                List.of("codigo", "nombre", "creditos", "sesiones"));

        return objeto(Map.of(
                "cursos", lista(curso, "Cada curso matriculado, una sola vez"),
                "observaciones", texto("Notas breves sobre datos ilegibles o ambiguos")),
                List.of("cursos", "observaciones"));
    }

    private static Map<String, Object> objeto(Map<String, Object> propiedades, List<String> requeridos) {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        // Orden estable de las propiedades (el de la lista de requeridos)
        Map<String, Object> ordenadas = new LinkedHashMap<>();
        requeridos.forEach(nombre -> ordenadas.put(nombre, propiedades.get(nombre)));
        esquema.put("properties", ordenadas);
        esquema.put("required", requeridos);
        return esquema;
    }

    private static Map<String, Object> texto(String descripcion) {
        return Map.of("type", "string", "description", descripcion);
    }

    private static Map<String, Object> entero(String descripcion) {
        return Map.of("type", "integer", "description", descripcion);
    }

    private static Map<String, Object> numero(String descripcion) {
        return Map.of("type", "number", "description", descripcion);
    }

    private static Map<String, Object> enumerado(List<String> valores) {
        return Map.of("type", "string", "enum", valores);
    }

    private static Map<String, Object> lista(Map<String, Object> elementos, String descripcion) {
        return Map.of("type", "array", "items", elementos, "description", descripcion);
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }
}

package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Resultado estructurado que devuelve la IA al leer un sílabo. */
public record SilaboExtraidoDto(
        @JsonPropertyDescription("Código del curso, ej. MT5307")
        String codigo,
        @JsonPropertyDescription("Nombre del curso, sin el código")
        String nombre,
        @JsonPropertyDescription("Número de créditos; 0 si el sílabo no lo indica")
        int creditos,
        @JsonPropertyDescription("Dificultad estimada del curso de 1 (fácil) a 5 (muy difícil), según su contenido")
        int dificultadEstimada,
        @JsonPropertyDescription("Temas o unidades del contenido del curso, en orden")
        List<TemaExtraidoDto> temas,
        @JsonPropertyDescription("Cada evaluación individual (EC1, EC2, L1, L2...), nunca agrupadas")
        List<EvaluacionExtraidaDto> evaluaciones,
        @JsonPropertyDescription("Horario semanal de clases, si figura en el sílabo; lista vacía si no")
        List<HorarioExtraidoDto> horarios,
        @JsonPropertyDescription("Notas breves en español sobre supuestos o datos ambiguos que el estudiante debe revisar")
        String observaciones) {
}

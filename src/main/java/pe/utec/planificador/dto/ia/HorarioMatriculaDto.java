package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Resultado estructurado que devuelve la IA al leer una foto de horario o un consolidado de matrícula. */
public record HorarioMatriculaDto(
        @JsonPropertyDescription("Cada curso matriculado, una sola vez aunque tenga varias sesiones")
        List<CursoMatriculadoDto> cursos,
        @JsonPropertyDescription("Notas breves en español sobre datos ilegibles o ambiguos que el estudiante debe revisar")
        String observaciones) {
}

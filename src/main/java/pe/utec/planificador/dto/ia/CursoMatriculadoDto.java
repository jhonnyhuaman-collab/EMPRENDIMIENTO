package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record CursoMatriculadoDto(
        @JsonPropertyDescription("Código del curso, ej. MT5307; cadena vacía si no figura")
        String codigo,
        @JsonPropertyDescription("Nombre del curso")
        String nombre,
        @JsonPropertyDescription("Créditos; 0 si no figuran")
        int creditos,
        @JsonPropertyDescription("Sesiones semanales de clase del curso (teoría, laboratorio, práctica...)")
        List<SesionClaseDto> sesiones) {
}

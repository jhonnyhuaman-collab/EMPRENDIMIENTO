package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record TemaExtraidoDto(
        @JsonPropertyDescription("Número de unidad (1, 2, 3...)")
        int unidad,
        @JsonPropertyDescription("Numeración del tema tal como figura en el sílabo, ej. \"6.2\"")
        String numero,
        @JsonPropertyDescription("Título del tema")
        String titulo) {
}

package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import pe.utec.planificador.model.TipoEvaluacion;

import java.util.List;

public record EvaluacionExtraidaDto(
        @JsonPropertyDescription("Nombre corto, ej. \"EC1\", \"Laboratorio L3\", \"Examen final\"")
        String nombre,
        TipoEvaluacion tipo,
        @JsonPropertyDescription("Semana del ciclo (1-16) en que se rinde o entrega")
        int semana,
        @JsonPropertyDescription("Peso en % del componente que la contiene sobre la nota final (ej. 60 si '3 EC 60%'); 100 si no pertenece a un componente")
        double pesoComponente,
        @JsonPropertyDescription("Peso en % de esta evaluación dentro de su componente (ej. 33 si 'EC1 33%'); si no pertenece a un componente, su peso directo en la nota final")
        double pesoDentroDelComponente,
        @JsonPropertyDescription("Números de los temas que evalúa, ej. [\"6.1\", \"6.2\"]")
        List<String> temas) {
}

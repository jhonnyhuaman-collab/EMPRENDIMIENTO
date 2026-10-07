package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.DayOfWeek;

public record HorarioExtraidoDto(
        DayOfWeek dia,
        @JsonPropertyDescription("Hora de inicio en formato HH:mm (24 h)")
        String horaInicio,
        @JsonPropertyDescription("Hora de fin en formato HH:mm (24 h)")
        String horaFin) {
}

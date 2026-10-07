package pe.utec.planificador.dto.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import pe.utec.planificador.model.FrecuenciaClase;

import java.time.DayOfWeek;

public record SesionClaseDto(
        DayOfWeek dia,
        @JsonPropertyDescription("Hora de inicio HH:mm (24 h)")
        String horaInicio,
        @JsonPropertyDescription("Hora de fin HH:mm (24 h)")
        String horaFin,
        @JsonPropertyDescription("Tipo de sesión y aula si figuran, ej. \"Teoría · A-505\"; cadena vacía si no hay")
        String detalle,
        @JsonPropertyDescription("SEMANA_A si la clase dice 'Semana A' (semanas impares), SEMANA_B si dice 'Semana B' (semanas pares); SEMANAL en otro caso")
        FrecuenciaClase frecuencia) {
}

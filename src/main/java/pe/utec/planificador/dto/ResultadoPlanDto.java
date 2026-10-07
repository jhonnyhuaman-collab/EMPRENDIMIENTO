package pe.utec.planificador.dto;

import lombok.Value;

import java.util.List;
import java.util.Locale;

/** Resumen de una generación del plan de estudio. */
@Value
public class ResultadoPlanDto {

    int bloquesCreados;
    double horasPlanificadas;
    List<String> advertencias;

    public String getMensaje() {
        return String.format(Locale.US, "Plan actualizado: %d bloques de estudio, %.1f h en total.",
                bloquesCreados, horasPlanificadas);
    }
}

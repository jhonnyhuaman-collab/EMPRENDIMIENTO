package pe.utec.planificador.dto;

import lombok.Value;

import java.util.Locale;

/** Resumen de una replanificación automática por atraso. */
@Value
public class ReplanificacionDto {

    int bloquesAtrasados;
    double horasAtrasadas;
    ResultadoPlanDto plan;

    public String getMensaje() {
        return String.format(Locale.US,
                "Quedaron %d %s sin hacer (%.1f h). Reorganizamos tu plan para que llegues a tiempo.",
                bloquesAtrasados, bloquesAtrasados == 1 ? "bloque" : "bloques", horasAtrasadas);
    }
}

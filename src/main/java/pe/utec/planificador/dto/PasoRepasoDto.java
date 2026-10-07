package pe.utec.planificador.dto;

import lombok.Value;

/** Un paso del plan de repaso de una evaluación. */
@Value
public class PasoRepasoDto {

    String titulo;
    String detalle;
    double horas;
}

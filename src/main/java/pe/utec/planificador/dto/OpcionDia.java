package pe.utec.planificador.dto;

import lombok.Value;

import java.time.DayOfWeek;

/** Día de la semana con su nombre en español, para los selectores. */
@Value
public class OpcionDia {

    DayOfWeek valor;
    String etiqueta;
}

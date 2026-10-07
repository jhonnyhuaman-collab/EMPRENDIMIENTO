package pe.utec.planificador.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/** Evento en el formato que espera FullCalendar. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventoCalendarioDto {

    String id;
    String title;
    String start;
    String end;
    Boolean allDay;
    String color;
    String url;
    List<String> classNames;
    String display;

    // Eventos recurrentes (horario de clases)
    List<Integer> daysOfWeek;
    String startTime;
    String endTime;
    String startRecur;
    String endRecur;

    /** Datos extra para mostrar en el evento (ej. aula). */
    Map<String, Object> extendedProps;
}

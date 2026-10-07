package pe.utec.planificador.dto;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/** Un día de la agenda con sus entregas, ej. "Mañana" o "Jueves 8 de octubre". */
@Value
public class DiaAgendaDto {

    LocalDate fecha;
    String titulo;
    int semana;
    List<EntregaProximaDto> entregas;
}

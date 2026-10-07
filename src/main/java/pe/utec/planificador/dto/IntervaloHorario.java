package pe.utec.planificador.dto;

import java.time.Duration;
import java.time.LocalTime;

/** Franja libre de un día [inicio, fin). */
public record IntervaloHorario(LocalTime inicio, LocalTime fin) {

    public long minutos() {
        return Duration.between(inicio, fin).toMinutes();
    }
}

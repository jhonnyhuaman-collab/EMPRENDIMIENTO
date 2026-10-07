package pe.utec.planificador.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Convierte semanas del sílabo en fechas reales y viceversa. */
@Service
public class CalendarioAcademicoService {

    public static final int TOTAL_SEMANAS = 16;

    /** Fecha del día indicado dentro de la semana N (semana 1 = fecha de inicio del ciclo). */
    public LocalDate fechaDeSemana(LocalDate inicioCiclo, int semana, DayOfWeek dia) {
        LocalDate inicioSemana = inicioCiclo.plusWeeks(semana - 1L);
        int desplazamiento = dia.getValue() - inicioCiclo.getDayOfWeek().getValue();
        if (desplazamiento < 0) {
            desplazamiento += 7;
        }
        return inicioSemana.plusDays(desplazamiento);
    }

    /** Semana del ciclo a la que pertenece una fecha (0 si es antes del inicio). */
    public int semanaDeFecha(LocalDate inicioCiclo, LocalDate fecha) {
        if (fecha.isBefore(inicioCiclo)) {
            return 0;
        }
        return (int) (ChronoUnit.DAYS.between(inicioCiclo, fecha) / 7) + 1;
    }
}

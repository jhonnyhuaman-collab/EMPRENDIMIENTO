package pe.utec.planificador.service;

import org.springframework.stereotype.Service;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Define cuándo empezar a preparar una evaluación y cuánto dedicarle como máximo por día. */
@Service
public class VentanaPreparacionService {

    private static final double HORAS_POR_DIA_DE_VENTANA = 1.5;
    private static final int DIAS_MINIMOS = 3;
    private static final int DIAS_MAXIMOS = 14;

    /** Días de preparación antes de la fecha: más horas → se empieza antes. */
    public int diasDePreparacion(Evaluacion evaluacion) {
        double horas = evaluacion.getHorasEstimadas() == null ? 0 : evaluacion.getHorasEstimadas();
        int dias = (int) Math.ceil(horas / HORAS_POR_DIA_DE_VENTANA) + 1;
        return Math.max(DIAS_MINIMOS, Math.min(DIAS_MAXIMOS, dias));
    }

    public LocalDate inicioRecomendado(Evaluacion evaluacion) {
        return inicioRecomendado(evaluacion, EnumSet.allOf(DayOfWeek.class));
    }

    /** Cuenta hacia atrás solo los días en que el estudiante estudia (ej. lunes a viernes). */
    public LocalDate inicioRecomendado(Evaluacion evaluacion, Set<DayOfWeek> diasEstudio) {
        Set<DayOfWeek> dias = diasEstudio.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : diasEstudio;
        int faltan = diasDePreparacion(evaluacion);
        LocalDate dia = evaluacion.getFecha();
        while (faltan > 0) {
            dia = dia.minusDays(1);
            if (dias.contains(dia.getDayOfWeek())) {
                faltan--;
            }
        }
        return dia;
    }

    /** Tope diario para una sola evaluación: así nunca se concentra todo en el último día. */
    public double maximoHorasPorDia(Evaluacion evaluacion) {
        TipoEvaluacion tipo = evaluacion.getTipo();
        return tipo == TipoEvaluacion.EXAMEN || tipo == TipoEvaluacion.PROYECTO ? 3.0 : 2.0;
    }
}

package pe.utec.planificador.service;

import org.springframework.stereotype.Service;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.NivelPrioridad;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Prioridad = cercanía de la fecha + peso en la nota + dificultad del curso (puntaje 0-100). */
@Service
public class PrioridadService {

    private static final double PESO_CERCANIA = 0.5;
    private static final double PESO_NOTA = 0.3;
    private static final double PESO_DIFICULTAD = 0.2;

    /** A partir de 30 días la cercanía ya no suma. */
    private static final int HORIZONTE_DIAS = 30;
    /** Un peso de 40% o más cuenta como máximo. */
    private static final double PESO_NOTA_MAXIMO = 40.0;

    public double puntaje(Evaluacion evaluacion, LocalDate dia) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(dia, evaluacion.getFecha()));
        double cercania = 1 - Math.min(dias, HORIZONTE_DIAS) / (double) HORIZONTE_DIAS;
        double peso = Math.min(valor(evaluacion.getPesoEnNotaFinal()), PESO_NOTA_MAXIMO) / PESO_NOTA_MAXIMO;
        double dificultad = evaluacion.getCurso().getDificultad() / 5.0;
        return 100 * (PESO_CERCANIA * cercania + PESO_NOTA * peso + PESO_DIFICULTAD * dificultad);
    }

    public NivelPrioridad nivel(Evaluacion evaluacion, LocalDate dia) {
        double puntaje = puntaje(evaluacion, dia);
        if (puntaje >= 65) return NivelPrioridad.ALTA;
        if (puntaje >= 45) return NivelPrioridad.MEDIA;
        return NivelPrioridad.BAJA;
    }

    private double valor(Double numero) {
        return numero == null ? 0 : numero;
    }
}

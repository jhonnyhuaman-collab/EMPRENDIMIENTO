package pe.utec.planificador.service;

import org.springframework.stereotype.Service;
import pe.utec.planificador.model.TipoEvaluacion;

/** Estima las horas de preparación de una evaluación según su tipo, dificultad y peso. */
@Service
public class EstimadorHorasService {

    public double estimarHoras(TipoEvaluacion tipo, int dificultadCurso, double pesoEnNotaFinal) {
        double base = switch (tipo) {
            case EXAMEN -> 10;
            case PROYECTO -> 12;
            case PRACTICA -> 5;
            case TAREA -> 4;
            case LABORATORIO -> 3;
        };
        double factorDificultad = 0.6 + 0.2 * dificultadCurso; // dificultad 2 → 1.0, 4 → 1.4
        double factorPeso = pesoEnNotaFinal >= 20 ? 1.2 : 1.0;
        return redondearMediaHora(base * factorDificultad * factorPeso);
    }

    private double redondearMediaHora(double horas) {
        return Math.max(0.5, Math.round(horas * 2) / 2.0);
    }
}

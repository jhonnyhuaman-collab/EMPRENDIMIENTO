package pe.utec.planificador.service;

import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.PasoRepasoDto;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.Tema;
import pe.utec.planificador.model.TipoEvaluacion;

import java.util.ArrayList;
import java.util.List;

/** Divide las horas de una evaluación en pasos de repaso (por tema, o genéricos según el tipo). */
@Service
public class PlanRepasoService {

    private static final double FRACCION_SIMULACRO = 0.2;

    public List<PasoRepasoDto> armarPlan(Evaluacion evaluacion) {
        double horas = evaluacion.getHorasEstimadas() == null ? 0 : evaluacion.getHorasEstimadas();
        List<Tema> temas = evaluacion.getTemas();
        if (temas.isEmpty()) {
            return pasosPorTipo(evaluacion.getTipo(), horas);
        }
        List<PasoRepasoDto> pasos = new ArrayList<>();
        double horasPorTema = horas * (1 - FRACCION_SIMULACRO) / temas.size();
        for (Tema tema : temas) {
            pasos.add(new PasoRepasoDto(tema.getNumero() + " " + tema.getTitulo(),
                    "Repasar teoría y resolver ejercicios del tema", redondear(horasPorTema)));
        }
        pasos.add(new PasoRepasoDto("Simulacro final",
                "Resolver evaluaciones pasadas con tiempo controlado", redondear(horas * FRACCION_SIMULACRO)));
        return pasos;
    }

    /** Pasos genéricos cuando la evaluación no tiene temas asociados. */
    private List<PasoRepasoDto> pasosPorTipo(TipoEvaluacion tipo, double horas) {
        return switch (tipo) {
            case LABORATORIO -> List.of(
                    new PasoRepasoDto("Leer la guía", "Revisar objetivos y fundamento teórico", redondear(horas * 0.2)),
                    new PasoRepasoDto("Preparar la práctica", "Simulación, código o montaje previo", redondear(horas * 0.5)),
                    new PasoRepasoDto("Redactar el informe", "Resultados, análisis y conclusiones", redondear(horas * 0.3)));
            case PROYECTO -> List.of(
                    new PasoRepasoDto("Definir alcance", "Objetivos, entregables y reparto de tareas", redondear(horas * 0.2)),
                    new PasoRepasoDto("Desarrollo del proyecto", "Avanzar el producto del proyecto", redondear(horas * 0.6)),
                    new PasoRepasoDto("Documento y exposición", "Informe final y ensayo de la presentación", redondear(horas * 0.2)));
            case TAREA -> List.of(
                    new PasoRepasoDto("Entender el enunciado", "Identificar qué se pide y cómo se califica", redondear(horas * 0.2)),
                    new PasoRepasoDto("Resolver la tarea", "Desarrollar la tarea", redondear(horas * 0.6)),
                    new PasoRepasoDto("Revisar y entregar", "Corregir formato y subir a tiempo", redondear(horas * 0.2)));
            case EXAMEN, PRACTICA -> List.of(
                    new PasoRepasoDto("Repasar teoría", "Apuntes, diapositivas y resúmenes", redondear(horas * 0.5)),
                    new PasoRepasoDto("Resolver ejercicios", "Problemas tipo de clase", redondear(horas * 0.3)),
                    new PasoRepasoDto("Simulacro final", "Evaluaciones pasadas con tiempo controlado", redondear(horas * 0.2)));
        };
    }

    private double redondear(double horas) {
        return Math.max(0.5, Math.round(horas * 2) / 2.0);
    }
}

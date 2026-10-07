package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.GuiaEvaluacionDto;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.Evaluacion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Arma la guía de una evaluación: temas, plan de repaso, prioridad y avance. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuiaEvaluacionService {

    private final EvaluacionService evaluacionService;
    private final PlanRepasoService planRepaso;
    private final VentanaPreparacionService ventanaPreparacion;
    private final PrioridadService prioridadService;

    public GuiaEvaluacionDto construirGuia(Long evaluacionId, LocalDate hoy) {
        Evaluacion evaluacion = evaluacionService.buscarPorId(evaluacionId);
        List<BloqueEstudio> bloques = evaluacion.getBloques().stream().filter(b -> !b.isReprogramado()).toList();

        return GuiaEvaluacionDto.builder()
                .evaluacion(evaluacion)
                .diasRestantes(ChronoUnit.DAYS.between(hoy, evaluacion.getFecha()))
                .inicioRecomendado(ventanaPreparacion.inicioRecomendado(evaluacion,
                        evaluacion.getCurso().getEstudiante().getDiasEstudioSet()))
                .prioridad(prioridadService.nivel(evaluacion, hoy))
                .planRepaso(planRepaso.armarPlan(evaluacion))
                .horasPlanificadas(bloques.stream().mapToDouble(BloqueEstudio::getDuracionHoras).sum())
                .horasCompletadas(bloques.stream()
                        .filter(BloqueEstudio::isCompletado)
                        .mapToDouble(BloqueEstudio::getDuracionHoras)
                        .sum())
                .build();
    }
}

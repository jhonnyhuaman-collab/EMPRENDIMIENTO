package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.EntregaProximaDto;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.repository.EvaluacionRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluacionService {

    private final EvaluacionRepository evaluacionRepository;
    private final EstudianteService estudianteService;
    private final PrioridadService prioridadService;

    public Evaluacion buscarPorId(Long id) {
        return evaluacionRepository.findByIdAndCursoEstudianteId(id, estudianteService.obtenerActual().getId())
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe la evaluación " + id));
    }

    /** Evaluaciones del estudiante entre hoy y hoy + (dias - 1). */
    public List<EntregaProximaDto> proximas(LocalDate hoy, int dias) {
        Long estudianteId = estudianteService.obtenerActual().getId();
        return evaluacionRepository
                .findByCursoEstudianteIdAndFechaBetweenOrderByFechaAsc(estudianteId, hoy, hoy.plusDays(dias - 1L))
                .stream()
                .map(e -> new EntregaProximaDto(e, ChronoUnit.DAYS.between(hoy, e.getFecha()),
                        prioridadService.nivel(e, hoy)))
                .toList();
    }
}

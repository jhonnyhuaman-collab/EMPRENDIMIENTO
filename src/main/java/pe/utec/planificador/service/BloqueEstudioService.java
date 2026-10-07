package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.repository.BloqueEstudioRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BloqueEstudioService {

    private final BloqueEstudioRepository bloqueRepository;
    private final PlanificadorService planificadorService;
    private final EstudianteService estudianteService;

    /**
     * Marca el bloque como completado o lo vuelve a dejar pendiente.
     * Si era un bloque reprogramado (lo hiciste tarde), el plan se recalcula para no estudiar dos veces lo mismo.
     */
    public void alternarCompletado(Long bloqueId, LocalDateTime ahora) {
        BloqueEstudio bloque = bloqueRepository.findByIdAndEvaluacionCursoEstudianteId(
                        bloqueId, estudianteService.obtenerActual().getId())
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el bloque " + bloqueId));
        boolean eraReprogramado = bloque.isReprogramado();
        bloque.setCompletado(!bloque.isCompletado());
        bloque.setReprogramado(false);
        if (eraReprogramado) {
            planificadorService.generarPlan(ahora);
        }
    }
}

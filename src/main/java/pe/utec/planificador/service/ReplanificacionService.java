package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.ReplanificacionDto;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.repository.BloqueEstudioRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Replanificación automática: si quedaron bloques de días pasados sin completar,
 * se marcan como reprogramados y sus horas se vuelven a repartir desde hoy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReplanificacionService {

    private final EstudianteService estudianteService;
    private final BloqueEstudioRepository bloqueRepository;
    private final PlanificadorService planificadorService;

    public Optional<ReplanificacionDto> replanificarSiHayAtrasos(LocalDateTime ahora) {
        Optional<Long> estudianteId = estudianteService.buscarActual().map(e -> e.getId());
        if (estudianteId.isEmpty()) {
            return Optional.empty(); // la app aún no tiene datos
        }
        List<BloqueEstudio> atrasados = bloqueRepository
                .findByEvaluacionCursoEstudianteIdAndFechaBeforeAndCompletadoFalseAndReprogramadoFalse(
                        estudianteId.get(), ahora.toLocalDate());
        if (atrasados.isEmpty()) {
            return Optional.empty();
        }
        atrasados.forEach(b -> b.setReprogramado(true));
        double horas = atrasados.stream().mapToDouble(BloqueEstudio::getDuracionHoras).sum();
        log.info("Replanificando: {} bloques atrasados ({} h)", atrasados.size(), horas);
        return Optional.of(new ReplanificacionDto(atrasados.size(), horas, planificadorService.generarPlan(ahora)));
    }
}

package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.repository.CursoRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CursoService {

    private final CursoRepository cursoRepository;
    private final EstudianteService estudianteService;
    private final PlanificadorService planificadorService;

    public List<Curso> listarCursos() {
        return cursoRepository.findByEstudianteIdOrderByCodigoAsc(estudianteService.obtenerActual().getId());
    }

    /** Elimina el curso con sus evaluaciones, temas, horario y bloques; luego replanifica con el tiempo liberado. */
    @Transactional
    public String eliminar(Long cursoId, LocalDateTime ahora) {
        Estudiante estudiante = estudianteService.obtenerActual();
        Curso curso = estudiante.getCursos().stream()
                .filter(c -> c.getId().equals(cursoId))
                .findFirst()
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el curso " + cursoId));
        estudiante.getCursos().remove(curso);
        cursoRepository.flush();
        planificadorService.generarPlan(ahora);
        return curso.getCodigo();
    }
}

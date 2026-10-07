package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.BloqueEstudio;

import java.time.LocalDate;
import java.util.List;

public interface BloqueEstudioRepository extends JpaRepository<BloqueEstudio, Long> {

    java.util.Optional<BloqueEstudio> findByIdAndEvaluacionCursoEstudianteId(Long id, Long estudianteId);

    List<BloqueEstudio> findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(Long estudianteId);

    List<BloqueEstudio> findByEvaluacionCursoEstudianteIdAndFechaOrderByHoraInicioAsc(
            Long estudianteId, LocalDate fecha);

    List<BloqueEstudio> findByEvaluacionCursoEstudianteIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long estudianteId, LocalDate desde, LocalDate hasta);

    /** Próximo bloque pendiente a partir de una fecha (para "tu próximo bloque es el jueves"). */
    java.util.Optional<BloqueEstudio> findFirstByEvaluacionCursoEstudianteIdAndFechaAfterAndCompletadoFalseAndReprogramadoFalseOrderByFechaAscHoraInicioAsc(
            Long estudianteId, LocalDate fecha);

    /** Bloques de días pasados que el estudiante no marcó ni fueron reprogramados aún. */
    List<BloqueEstudio> findByEvaluacionCursoEstudianteIdAndFechaBeforeAndCompletadoFalseAndReprogramadoFalse(
            Long estudianteId, LocalDate fecha);
}

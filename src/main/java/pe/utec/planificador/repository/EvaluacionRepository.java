package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.Evaluacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {

    Optional<Evaluacion> findByIdAndCursoEstudianteId(Long id, Long estudianteId);

    List<Evaluacion> findByCursoEstudianteIdOrderByFechaAsc(Long estudianteId);

    List<Evaluacion> findByCursoEstudianteIdAndFechaBetweenOrderByFechaAsc(
            Long estudianteId, LocalDate desde, LocalDate hasta);
}

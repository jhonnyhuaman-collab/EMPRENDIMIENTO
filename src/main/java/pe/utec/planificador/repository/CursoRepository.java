package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.Curso;

import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long> {

    List<Curso> findByEstudianteIdOrderByCodigoAsc(Long estudianteId);
}

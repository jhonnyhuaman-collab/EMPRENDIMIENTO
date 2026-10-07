package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.HorarioClase;

import java.util.List;

public interface HorarioClaseRepository extends JpaRepository<HorarioClase, Long> {

    List<HorarioClase> findByCursoEstudianteId(Long estudianteId);
}

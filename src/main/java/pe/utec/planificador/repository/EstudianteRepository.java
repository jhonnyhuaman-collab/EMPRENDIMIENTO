package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.Estudiante;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    Optional<Estudiante> findFirstByOrderByIdAsc();

    Optional<Estudiante> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estudiante e where e.id = (select min(s.id) from Estudiante s)")
    Optional<Estudiante> perfilParaRegistro();
}

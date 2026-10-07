package pe.utec.planificador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.utec.planificador.model.Tema;

public interface TemaRepository extends JpaRepository<Tema, Long> {
}

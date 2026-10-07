package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.repository.EstudianteRepository;
import java.util.Optional;

/** Resuelve el estudiante de la sesion; al inicializar la app usa el perfil inicial. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstudianteService {

    private final EstudianteRepository estudianteRepository;

    public Estudiante obtenerActual() {
        return buscarActual()
                .orElseThrow(() -> new EntidadNoEncontradaException("No hay ningún estudiante registrado"));
    }

    public Optional<Estudiante> buscarActual() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken)) {
            return Optional.of(estudianteRepository.findByEmailIgnoreCase(autenticacion.getName())
                    .orElseThrow(() -> new EntidadNoEncontradaException("No existe el perfil de esta cuenta")));
        }
        return estudianteRepository.findFirstByOrderByIdAsc();
    }
}

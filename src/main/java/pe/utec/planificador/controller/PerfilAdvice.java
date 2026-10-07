package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.utec.planificador.service.EstudianteService;
import java.security.Principal;

@ControllerAdvice
@RequiredArgsConstructor
public class PerfilAdvice {
    private final EstudianteService estudiantes;

    @ModelAttribute("nombrePerfil")
    public String nombre(Principal principal) {
        return principal == null ? "Estudiante" : estudiantes.obtenerActual().getNombre();
    }

    @ModelAttribute("correoPerfil")
    public String correo(Principal principal) {
        return principal == null ? "" : estudiantes.obtenerActual().getEmail();
    }
}

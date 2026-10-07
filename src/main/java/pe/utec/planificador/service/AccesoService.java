package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.repository.EstudianteRepository;
import pe.utec.planificador.model.Estudiante;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccesoService {
    private final EstudianteRepository estudiantes;
    private final PasswordEncoder encoder;

    @Transactional
    public void registrar(String correo, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new SilaboException("La contrasena es demasiado larga. Usa menos caracteres.");
        }
        String email = correo.strip().toLowerCase(Locale.ROOT);
        // Solo la primera cuenta adopta el perfil previo; las siguientes empiezan vacias.
        var previo = estudiantes.perfilParaRegistro().orElse(null);
        var existente = estudiantes.findByEmailIgnoreCase(email);
        if (existente.isPresent() && (existente.get().getPasswordHash() != null
                || previo == null || !existente.get().getId().equals(previo.getId()))) {
            throw new SilaboException("Este correo ya tiene una cuenta. Inicia sesion o usa otro correo.");
        }
        Estudiante estudiante = previo != null && previo.getPasswordHash() == null ? previo
                : Estudiante.builder().fechaInicioCiclo(LocalDate.of(2026, 8, 10))
                        .horasEstudioPorDia(3.0).horaDormir(LocalTime.of(23, 0)).build();
        estudiante.setEmail(email);
        estudiante.setNombre(nombreDesdeCorreo(correo));
        estudiante.setPasswordHash(encoder.encode(password));
        estudiantes.save(estudiante);
    }

    public static String nombreDesdeCorreo(String correo) {
        String local = correo.strip().split("@", 2)[0].replaceAll("[._+\\-]+", " ").replaceAll("\\d+", " ").strip();
        if (local.isBlank()) return "Estudiante";
        return Arrays.stream(local.split("\\s+")).limit(3)
                .map(p -> p.substring(0, 1).toUpperCase(Locale.ROOT) + p.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}

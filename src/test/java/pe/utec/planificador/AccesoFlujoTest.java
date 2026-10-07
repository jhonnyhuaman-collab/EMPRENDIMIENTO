package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.service.AccesoService;
import pe.utec.planificador.service.EstudianteService;
import pe.utec.planificador.repository.EstudianteRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:acceso;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AccesoFlujoTest {
    @Autowired MockMvc mvc;
    @Autowired EstudianteService estudiantes;
    @Autowired AccesoService acceso;
    @Autowired PasswordEncoder encoder;
    @Autowired EstudianteRepository repositorio;

    @Test
    void protegePantallasYPermiteLogin() throws Exception {
        mvc.perform(get("/agenda")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Crear cuenta")));
        mvc.perform(get("/registro")).andExpect(status().isOk());
    }

    @Test
    void registroLoginPerfilYCierreDeSesion() throws Exception {
        var id = estudiantes.obtenerActual().getId();
        mvc.perform(post("/registro").with(csrf()).param("email", "Jhonny.Perez@utec.edu.pe")
                        .param("password", "PruebaSegura123"))
                .andExpect(redirectedUrl("/login?registrado"));
        var estudiante = estudiantes.obtenerActual();
        assertThat(estudiante.getId()).isEqualTo(id);
        assertThat(estudiante.getNombre()).isEqualTo("Jhonny Perez");
        assertThat(estudiante.getEmail()).isEqualTo("jhonny.perez@utec.edu.pe");
        assertThat(encoder.matches("PruebaSegura123", estudiante.getPasswordHash())).isTrue();
        var resultado = mvc.perform(post("/login").with(csrf()).param("email", "JHONNY.PEREZ@utec.edu.pe")
                .param("password", "PruebaSegura123")).andExpect(redirectedUrl("/")).andReturn();
        var sesion = (MockHttpSession) resultado.getRequest().getSession(false);
        mvc.perform(get("/agenda").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Jhonny Perez")))
                .andExpect(content().string(containsString("jhonny.perez@utec.edu.pe")));
        mvc.perform(post("/logout").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salida"));
        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(get("/login")).andExpect(content().string(containsString("Crear cuenta")));
        mvc.perform(get("/registro")).andExpect(status().isOk());
        mvc.perform(post("/registro").with(csrf()).param("email", "otro@utec.edu.pe")
                .param("password", "OtraClave123")).andExpect(redirectedUrl("/login?registrado"));
        assertThat(estudiante.getEmail()).isEqualTo("jhonny.perez@utec.edu.pe");
        var otro = repositorio.findByEmailIgnoreCase("otro@utec.edu.pe").orElseThrow();
        assertThat(otro.getId()).isNotEqualTo(id);
        assertThat(otro.getCursos()).isEmpty();
        var segundoLogin = mvc.perform(post("/login").with(csrf()).param("email", "otro@utec.edu.pe")
                .param("password", "OtraClave123")).andExpect(redirectedUrl("/")).andReturn();
        var segundaSesion = (MockHttpSession) segundoLogin.getRequest().getSession(false);
        mvc.perform(get("/").session(segundaSesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("otro@utec.edu.pe")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("jhonny.perez@utec.edu.pe"))));
    }

    @Test
    void rechazaClaveIncorrectaYPostSinCsrf() throws Exception {
        acceso.registrar("ana@utec.edu.pe", "PruebaSegura123");
        mvc.perform(post("/login").with(csrf()).param("email", "ana@utec.edu.pe").param("password", "incorrecta"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/registro").param("email", "otro@utec.edu.pe").param("password", "ClaveSegura123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaRegistroAntesDeGuardar() throws Exception {
        mvc.perform(post("/registro").with(csrf()).param("email", "correo-invalido").param("password", "123"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("cuenta", "email", "password"));
        assertThat(estudiantes.obtenerActual().getPasswordHash()).isNull();
        assertThat(AccesoService.nombreDesdeCorreo("12345@utec.edu.pe")).isEqualTo("Estudiante");
    }

    @Test
    void rechazaCorreosDuplicadosSinCambiarLaClave() throws Exception {
        acceso.registrar("ana@utec.edu.pe", "PruebaSegura123");
        mvc.perform(post("/registro").with(csrf()).param("email", "ANA@utec.edu.pe").param("password", "OtraClave123"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Este correo ya tiene una cuenta")));
        assertThat(repositorio.count()).isEqualTo(1);
        assertThat(encoder.matches("PruebaSegura123", repositorio.findByEmailIgnoreCase("ana@utec.edu.pe").orElseThrow().getPasswordHash())).isTrue();
    }
}

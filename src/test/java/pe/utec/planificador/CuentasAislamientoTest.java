package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.repository.BloqueEstudioRepository;
import pe.utec.planificador.repository.EstudianteRepository;
import pe.utec.planificador.service.AccesoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cuentas;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc
@Import(RelojDePrueba.class)
@Transactional
class CuentasAislamientoTest {
    @Autowired MockMvc mvc;
    @Autowired AccesoService acceso;
    @Autowired EstudianteRepository estudiantes;
    @Autowired BloqueEstudioRepository bloques;

    @Test
    void nuevaCuentaTieneSuPropioPerfilYNoReplanificaLaAnterior() throws Exception {
        acceso.registrar("dueno@utec.edu.pe", "ClaveSegura123");
        var dueno = estudiantes.findByEmailIgnoreCase("dueno@utec.edu.pe").orElseThrow();
        var atrasado = bloques.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(dueno.getId()).get(0);
        atrasado.setFecha(RelojDePrueba.AHORA.toLocalDate().minusDays(1));
        atrasado.setReprogramado(false);
        acceso.registrar("ana.diaz@utec.edu.pe", "ClaveSegura123");
        var ana = estudiantes.findByEmailIgnoreCase("ana.diaz@utec.edu.pe").orElseThrow();
        assertThat(ana.getCursos()).isEmpty();
        assertThat(dueno.getCursos()).hasSize(3);
        mvc.perform(get("/").with(user(ana.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Diaz")))
                .andExpect(content().string(not(containsString("Control Inteligente"))));
        mvc.perform(get("/api/eventos").with(user(ana.getEmail()))).andExpect(content().json("[]"));
        assertThat(atrasado.isReprogramado()).isFalse();
        mvc.perform(get("/cursos").with(user(dueno.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Control Inteligente")));
    }

    @Test
    void noPuedeConsultarNiCambiarDatosDeOtraCuentaPorSuId() throws Exception {
        acceso.registrar("dueno@utec.edu.pe", "ClaveSegura123");
        var dueno = estudiantes.findByEmailIgnoreCase("dueno@utec.edu.pe").orElseThrow();
        var curso = dueno.getCursos().get(0);
        var evaluacion = curso.getEvaluaciones().get(0);
        var bloque = bloques.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(dueno.getId()).get(0);
        acceso.registrar("ana@utec.edu.pe", "ClaveSegura123");
        var ana = user("ana@utec.edu.pe");
        mvc.perform(get("/evaluaciones/" + evaluacion.getId()).with(ana)).andExpect(status().isNotFound());
        mvc.perform(get("/agenda/" + evaluacion.getId() + "/editar").with(ana)).andExpect(status().isNotFound());
        mvc.perform(post("/agenda/" + evaluacion.getId() + "/completar").with(ana).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/agenda/" + evaluacion.getId() + "/eliminar").with(ana).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/bloques/" + bloque.getId() + "/alternar").with(ana).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/cursos/" + curso.getId() + "/eliminar").with(ana).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/agenda").with(ana).with(csrf()).param("cursoId", curso.getId().toString())
                        .param("nombre", "Tarea ajena").param("tipo", "TAREA").param("fecha", "2026-10-10"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/h2-console/").with(ana)).andExpect(status().isForbidden());
        assertThat(evaluacion.isCompletada()).isFalse();
        assertThat(bloque.isCompletado()).isFalse();
        assertThat(dueno.getCursos()).hasSize(3);
    }
}

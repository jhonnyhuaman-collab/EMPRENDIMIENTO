package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.utec.planificador.service.CursoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con la configuración normal (sin cursos de ejemplo) la app arranca vacía e invita a subir un sílabo. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vacia;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
class AppVaciaTest {

    @Autowired private MockMvc mvc;
    @Autowired private CursoService cursoService;

    @Test
    void arrancaSinCursosYTodasLasPantallasCargan() throws Exception {
        assertThat(cursoService.listarCursos()).isEmpty();
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Subir mi primer sílabo")));
        mvc.perform(get("/cursos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aún no tienes cursos")));
        mvc.perform(get("/calendario")).andExpect(status().isOk());
        mvc.perform(get("/api/eventos")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/plan/exportar.ics")).andExpect(status().isOk());
    }
}

package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.service.CursoService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flujo de la Fase 3 sin IA: pantallas, guardar la tabla revisada, validación y reemplazo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:silabos;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
@Transactional
class SilaboFlujoTest {

    @Autowired private MockMvc mvc;
    @Autowired private CursoService cursoService;

    @Test
    void muestraLasPantallasDeSubidaYRegistroManual() throws Exception {
        mvc.perform(get("/silabos/subir")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sube tu sílabo")));
        mvc.perform(get("/silabos/manual")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisa antes de guardar")));
    }

    @Test
    void guardaElCursoRevisadoYGeneraSuPlan() throws Exception {
        mvc.perform(post("/silabos/guardar")
                        .param("codigo", "fi2001").param("nombre", "Física II")
                        .param("creditos", "4").param("dificultad", "4").param("color", "#A44A6B")
                        .param("temas[0].unidad", "1").param("temas[0].numero", "1.1").param("temas[0].titulo", "Electrostática")
                        .param("temas[1].unidad", "1").param("temas[1].numero", "1.2").param("temas[1].titulo", "Ley de Gauss")
                        .param("evaluaciones[0].nombre", "PC1").param("evaluaciones[0].tipo", "PRACTICA")
                        .param("evaluaciones[0].semana", "10").param("evaluaciones[0].dia", "FRIDAY")
                        .param("evaluaciones[0].pesoEnNotaFinal", "100").param("evaluaciones[0].horasEstimadas", "6")
                        .param("evaluaciones[0].temas", "1.1, 1.2, 9.9")
                        .param("horarios[0].dia", "MONDAY").param("horarios[0].horaInicio", "18:00").param("horarios[0].horaFin", "20:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cursos"))
                .andExpect(flash().attributeExists("resultadoPlan"));

        Curso fisica = buscar("FI2001");
        Evaluacion pc1 = fisica.getEvaluaciones().get(0);
        assertThat(pc1.getFecha()).isEqualTo(LocalDate.of(2026, 10, 16)); // viernes de la semana 10 (inicio 10/08)
        assertThat(pc1.getTemas()).extracting("numero").containsExactly("1.1", "1.2"); // 9.9 no existe
        assertThat(pc1.getBloques()).isNotEmpty();
        assertThat(fisica.getHorarios()).hasSize(1);
    }

    @Test
    void reemplazaUnCursoExistenteConElMismoCodigo() throws Exception {
        mvc.perform(post("/silabos/guardar")
                        .param("codigo", "MT5307").param("nombre", "Control Inteligente v2")
                        .param("creditos", "4").param("dificultad", "4").param("color", "#7B5EA7")
                        .param("evaluaciones[0].nombre", "Examen único").param("evaluaciones[0].tipo", "EXAMEN")
                        .param("evaluaciones[0].semana", "12").param("evaluaciones[0].dia", "MONDAY")
                        .param("evaluaciones[0].pesoEnNotaFinal", "100").param("evaluaciones[0].horasEstimadas", "10"))
                .andExpect(status().is3xxRedirection());

        assertThat(cursoService.listarCursos()).filteredOn(c -> c.getCodigo().equals("MT5307")).hasSize(1);
        assertThat(buscar("MT5307").getNombre()).isEqualTo("Control Inteligente v2");
        assertThat(buscar("MT5307").getEvaluaciones()).hasSize(1);
    }

    @Test
    void eliminaUnCursoConTodoLoSuyo() throws Exception {
        Long id = buscar("IN3020").getId();
        mvc.perform(post("/cursos/" + id + "/eliminar"))
                .andExpect(redirectedUrl("/cursos"))
                .andExpect(flash().attribute("cursoEliminado", "IN3020"));
        assertThat(cursoService.listarCursos()).extracting("codigo").doesNotContain("IN3020").hasSize(2);
    }

    @Test
    void vuelveALaRevisionSiHayErrores() throws Exception {
        mvc.perform(post("/silabos/guardar")
                        .param("codigo", "").param("nombre", "Sin código")
                        .param("creditos", "3").param("dificultad", "3").param("color", "#7B5EA7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hay campos por corregir")))
                .andExpect(content().string(containsString("Agrega al menos una evaluación")));
    }

    private Curso buscar(String codigo) {
        return cursoService.listarCursos().stream().filter(c -> c.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }
}

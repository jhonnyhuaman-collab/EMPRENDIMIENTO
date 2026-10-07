package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.service.CursoService;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Arranca la app con H2 en memoria y verifica que las pantallas cargan con los datos de ejemplo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pruebas;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(RelojDePrueba.class)
@Transactional
class PantallasTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CursoService cursoService;

    @Test
    void dataLoaderCargaTresCursosConPesosQueSuman100() {
        assertThat(cursoService.listarCursos()).hasSize(3)
                .allSatisfy(c -> assertThat(c.getPesoTotal()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01)));
        Curso control = cursoService.listarCursos().stream()
                .filter(c -> c.getCodigo().equals("MT5307")).findFirst().orElseThrow();
        assertThat(control.getEvaluaciones()).hasSize(11);
        assertThat(control.getTemas()).hasSize(6);
    }

    @Test
    void cargaPanelHoy() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Hoy")));
    }

    @Test
    void cargaMisCursos() throws Exception {
        mvc.perform(get("/cursos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Control Inteligente")));
    }

    @Test
    void cargaCalendarioYEventos() throws Exception {
        mvc.perform(get("/calendario")).andExpect(status().isOk());
        mvc.perform(get("/api/eventos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title =~ /.*EC1.*/)]").exists());
    }

    @Test
    void cargaDetalleDeEvaluacion() throws Exception {
        Long id = cursoService.listarCursos().get(0).getEvaluaciones().get(0).getId();
        mvc.perform(get("/evaluaciones/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Plan de repaso")));
    }

    @Test
    void replanificaAutomaticamenteAlAbrirHoySiHayAtrasos() throws Exception {
        Evaluacion futura = cursoService.listarCursos().get(0).getEvaluaciones().stream()
                .filter(e -> e.getFecha().isAfter(LocalDate.of(2026, 10, 10))).findFirst().orElseThrow();
        futura.agregarBloque(BloqueEstudio.builder()
                .fecha(LocalDate.of(2026, 10, 3)) // ayer, sin completar
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(11, 0))
                .descripcion("Bloque atrasado").build());

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu plan se reorganizó automáticamente")));
        // En la siguiente visita ya no aparece
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Tu plan se reorganizó"))));
    }

    @Test
    void evaluacionInexistenteDa404() throws Exception {
        mvc.perform(get("/evaluaciones/99999")).andExpect(status().isNotFound());
    }
}

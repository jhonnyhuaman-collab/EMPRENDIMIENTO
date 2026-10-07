package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.repository.BloqueEstudioRepository;
import pe.utec.planificador.service.CursoService;
import pe.utec.planificador.service.EstudianteService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cambiar el inicio del ciclo recalcula las fechas de las evaluaciones manteniendo su semana y día. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:configuracion;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
@Transactional
class ConfiguracionTest {

    @Autowired private MockMvc mvc;
    @Autowired private CursoService cursoService;
    @Autowired private EstudianteService estudianteService;
    @Autowired private BloqueEstudioRepository bloqueRepository;

    @Test
    void conInicioEl10DeAgostoHoyEsSemana8() throws Exception {
        mvc.perform(get("/configuracion")).andExpect(status().isOk())
                .andExpect(content().string(containsString("semana 8")))
                .andExpect(content().string(containsString("Semana B")));
    }

    @Test
    void cambiarElInicioRecalculaLasFechas() throws Exception {
        Evaluacion ec1 = ec1();
        assertThat(ec1.getFecha()).isEqualTo(LocalDate.of(2026, 9, 14)); // semana 6, lunes (inicio 10/08)

        mvc.perform(post("/configuracion")
                        .param("fechaInicioCiclo", "2026-08-19")   // miércoles: se usa el lunes 17/08
                        .param("horasEstudioPorDia", "3")
                        .param("horaDormir", "22:30")
                        .param("cargaMaximaDiaria", "9")
                        .param("horasMaximasCursoSemana", "4")
                        .param("diasEstudio", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
                .andExpect(redirectedUrl("/configuracion"))
                .andExpect(flash().attributeExists("resultadoPlan"));

        assertThat(estudianteService.obtenerActual().getFechaInicioCiclo()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(estudianteService.obtenerActual().getHoraDormir()).isEqualTo(LocalTime.of(22, 30));
        assertThat(ec1().getFecha()).isEqualTo(LocalDate.of(2026, 9, 21)); // sigue siendo semana 6, lunes
    }

    @Test
    void guardarDiasDeEstudioConservaLaSeleccion() throws Exception {
        mvc.perform(post("/configuracion")
                        .param("fechaInicioCiclo", "2026-08-10")
                        .param("horasEstudioPorDia", "3")
                        .param("horaDormir", "23:00")
                        .param("cargaMaximaDiaria", "9")
                        .param("horasMaximasCursoSemana", "4")
                        .param("_diasEstudio", "on")
                        .param("diasEstudio", "MONDAY", "WEDNESDAY", "SATURDAY"))
                .andExpect(redirectedUrl("/configuracion"))
                .andExpect(flash().attribute("configuracionGuardada", true));

        assertThat(estudianteService.obtenerActual().getDiasEstudioSet())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY);
        assertThat(bloqueRepository.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(
                estudianteService.obtenerActual().getId()))
                .isNotEmpty()
                .allSatisfy(bloque -> assertThat(bloque.getFecha().getDayOfWeek())
                        .isIn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY));
        mvc.perform(get("/configuracion"))
                .andExpect(model().attribute("configuracion", hasProperty("diasEstudio",
                        containsInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))));
    }

    @Test
    void sinDiasDeEstudioMuestraErrorSinCambiarLaConfiguracion() throws Exception {
        var diasAntes = estudianteService.obtenerActual().getDiasEstudioSet();

        mvc.perform(post("/configuracion")
                        .param("fechaInicioCiclo", "2026-08-10")
                        .param("horasEstudioPorDia", "3")
                        .param("horaDormir", "23:00")
                        .param("cargaMaximaDiaria", "9")
                        .param("horasMaximasCursoSemana", "4")
                        .param("_diasEstudio", "on"))
                .andExpect(status().isOk())
                .andExpect(view().name("configuracion"))
                .andExpect(model().attributeHasFieldErrors("configuracion", "diasEstudio"));

        assertThat(estudianteService.obtenerActual().getDiasEstudioSet()).isEqualTo(diasAntes);
    }

    private Evaluacion ec1() {
        return cursoService.listarCursos().stream().filter(c -> c.getCodigo().equals("MT5307")).findFirst().orElseThrow()
                .getEvaluaciones().stream().filter(e -> e.getNombre().equals("EC1")).findFirst().orElseThrow();
    }
}

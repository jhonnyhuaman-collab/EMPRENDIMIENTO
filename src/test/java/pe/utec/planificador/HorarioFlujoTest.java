package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.FrecuenciaClase;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.service.CursoService;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Importación de horario (sin IA): pantalla, guardado, colores y conservación de evaluaciones. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:horario;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
@Transactional
class HorarioFlujoTest {

    @Autowired private MockMvc mvc;
    @Autowired private CursoService cursoService;

    @Test
    void muestraElHorarioSemanalPorColores() throws Exception {
        mvc.perform(get("/horario")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi horario")))
                .andExpect(content().string(containsString("Control Inteligente")));
        mvc.perform(get("/api/horario")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].daysOfWeek").isArray())
                .andExpect(jsonPath("$[0].color").exists());
    }

    @Test
    void creaCursosNuevosYActualizaElHorarioDeLosExistentesSinPerderEvaluaciones() throws Exception {
        mvc.perform(post("/horario/guardar")
                        .param("cursos[0].codigo", "MT5307").param("cursos[0].nombre", "Control Inteligente")
                        .param("cursos[0].creditos", "4").param("cursos[0].color", "#A44A6B").param("cursos[0].existente", "true")
                        .param("cursos[1].codigo", "fi2001").param("cursos[1].nombre", "Física II")
                        .param("cursos[1].creditos", "4").param("cursos[1].color", "#2F7F86").param("cursos[1].existente", "false")
                        .param("sesiones[0].codigoCurso", "MT5307").param("sesiones[0].dia", "WEDNESDAY")
                        .param("sesiones[0].horaInicio", "07:00").param("sesiones[0].horaFin", "09:00")
                        .param("sesiones[0].detalle", "Teoría · A-505")
                        .param("sesiones[1].codigoCurso", "FI2001").param("sesiones[1].dia", "FRIDAY")
                        .param("sesiones[1].horaInicio", "16:00").param("sesiones[1].horaFin", "18:00")
                        .param("sesiones[1].frecuencia", "SEMANA_B"))
                .andExpect(redirectedUrl("/horario"))
                .andExpect(flash().attributeExists("resultadoHorario"));

        Curso control = buscar("MT5307");
        assertThat(control.getEvaluaciones()).hasSize(11);            // se conservan
        assertThat(control.getColor()).isEqualTo("#A44A6B");
        assertThat(control.getHorarios()).singleElement().satisfies(h -> {
            assertThat(h.getDiaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(h.getDetalle()).isEqualTo("Teoría · A-505");
        });

        Curso fisica = buscar("FI2001");
        assertThat(fisica.getEvaluaciones()).isEmpty();
        assertThat(fisica.getHorarios()).extracting(HorarioClase::getHoraInicio).containsExactly(LocalTime.of(16, 0));
        assertThat(fisica.getHorarios().get(0).getFrecuencia()).isEqualTo(FrecuenciaClase.SEMANA_B);

        // En "Mi horario", la clase de Semana B no aparece en Semana A
        mvc.perform(get("/api/horario").param("semana", "A"))
                .andExpect(jsonPath("$[?(@.title =~ /Física II.*/)]").isEmpty());
        mvc.perform(get("/api/horario").param("semana", "B"))
                .andExpect(jsonPath("$[?(@.title =~ /Física II.*/)].extendedProps.detalle").value(org.hamcrest.Matchers.hasItem("Sem B")));
        mvc.perform(get("/horario")).andExpect(content().string(containsString("Semana B")));

        mvc.perform(get("/cursos")).andExpect(content().string(containsString("Falta el sílabo de este curso")));
    }

    @Test
    void avisaSiUnaClaseNoTieneCursoValido() throws Exception {
        mvc.perform(post("/horario/guardar")
                        .param("cursos[0].codigo", "FI2001").param("cursos[0].nombre", "Física II")
                        .param("cursos[0].creditos", "4").param("cursos[0].color", "#2F7F86")
                        .param("sesiones[0].codigoCurso", "XX9999").param("sesiones[0].dia", "MONDAY")
                        .param("sesiones[0].horaInicio", "08:00").param("sesiones[0].horaFin", "10:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no está en la lista de cursos")));
        assertThat(cursoService.listarCursos()).extracting("codigo").doesNotContain("FI2001");
    }

    @Test
    void alSubirElSilaboSeConservaElHorarioImportado() throws Exception {
        mvc.perform(post("/silabos/guardar")
                        .param("codigo", "MT5307").param("nombre", "Control Inteligente")
                        .param("creditos", "4").param("dificultad", "4").param("color", "#7B5EA7")
                        .param("evaluaciones[0].nombre", "EC1").param("evaluaciones[0].tipo", "EXAMEN")
                        .param("evaluaciones[0].semana", "12").param("evaluaciones[0].dia", "MONDAY")
                        .param("evaluaciones[0].pesoEnNotaFinal", "100").param("evaluaciones[0].horasEstimadas", "10"))
                .andExpect(redirectedUrl("/cursos"));

        assertThat(buscar("MT5307").getHorarios()).hasSize(2); // lunes y jueves del horario original
    }

    @Test
    void elSilaboDeUnCursoImportadoUsaLosDiasDeSuHorario() throws Exception {
        // Horario importado sin código: el curso se reconoce por el nombre
        mvc.perform(post("/horario/guardar")
                        .param("cursos[0].codigo", "FDS").param("cursos[0].nombre", "Física del Sólido")
                        .param("cursos[0].creditos", "3").param("cursos[0].color", "#2F7F86")
                        .param("sesiones[0].codigoCurso", "FDS").param("sesiones[0].dia", "TUESDAY")
                        .param("sesiones[0].horaInicio", "08:00").param("sesiones[0].horaFin", "10:00")
                        .param("sesiones[0].detalle", "Teoría · A-101").param("sesiones[0].frecuencia", "SEMANAL")
                        .param("sesiones[1].codigoCurso", "FDS").param("sesiones[1].dia", "FRIDAY")
                        .param("sesiones[1].horaInicio", "14:00").param("sesiones[1].horaFin", "16:00")
                        .param("sesiones[1].detalle", "Laboratorio · L-3").param("sesiones[1].frecuencia", "SEMANA_A"))
                .andExpect(redirectedUrl("/horario"));

        var leido = new pe.utec.planificador.dto.ia.SilaboExtraidoDto("FI3050", "Física del Sólido", 3, 4, java.util.List.of(),
                java.util.List.of(
                        new pe.utec.planificador.dto.ia.EvaluacionExtraidaDto("PC1", pe.utec.planificador.model.TipoEvaluacion.PRACTICA, 5, 100, 20, java.util.List.of()),
                        new pe.utec.planificador.dto.ia.EvaluacionExtraidaDto("Lab 1", pe.utec.planificador.model.TipoEvaluacion.LABORATORIO, 3, 100, 10, java.util.List.of()),
                        new pe.utec.planificador.dto.ia.EvaluacionExtraidaDto("Lab 2", pe.utec.planificador.model.TipoEvaluacion.LABORATORIO, 4, 100, 10, java.util.List.of())),
                java.util.List.of(new pe.utec.planificador.dto.ia.HorarioExtraidoDto(DayOfWeek.MONDAY, "07:00", "09:00")), "");
        var formulario = preparacionSilabo.desdeIa(leido, "silabo.pdf");

        assertThat(formulario.getCodigoExistente()).isEqualTo("FDS");
        assertThat(formulario.getColor()).isEqualTo("#2F7F86");
        assertThat(formulario.getHorarios()).isEmpty(); // manda el horario importado, no el del sílabo
        assertThat(formulario.getEvaluaciones()).extracting("dia")
                .containsExactly(DayOfWeek.TUESDAY,   // práctica → día de teoría
                        DayOfWeek.FRIDAY,             // laboratorio en semana 3 (A) → viernes de laboratorio
                        DayOfWeek.FRIDAY);            // semana 4 (B): igual va el día de laboratorio
    }

    @Autowired private pe.utec.planificador.service.PreparacionSilaboService preparacionSilabo;

    private Curso buscar(String codigo) {
        return cursoService.listarCursos().stream().filter(c -> c.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }
}

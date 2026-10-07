package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.OrigenEvaluacion;
import pe.utec.planificador.service.CursoService;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Agenda: agregar, editar, marcar como entregado y eliminar pendientes que no estaban en el sílabo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:agenda;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
@Transactional
class AgendaFlujoTest {

    @Autowired private MockMvc mvc;
    @Autowired private CursoService cursoService;

    @Test
    void agregarUnaTareaLaPoneEnLaAgendaYEnElPlan() throws Exception {
        Curso control = buscar("MT5307");
        mvc.perform(post("/agenda")
                        .param("cursoId", control.getId().toString())
                        .param("nombre", "Informe de robótica")
                        .param("tipo", "TAREA")
                        .param("fecha", "2026-10-09")          // viernes
                        .param("horaEntrega", "23:59")
                        .param("notas", "En grupo, subir a Canvas"))
                .andExpect(redirectedUrl("/agenda"))
                .andExpect(flash().attribute("mensajeAgenda", containsString("Informe de robótica")));

        Evaluacion tarea = tarea(control, "Informe de robótica");
        assertThat(tarea.getOrigen()).isEqualTo(OrigenEvaluacion.AGENDA);
        assertThat(tarea.getSemana()).isEqualTo(9);                     // inicio 10/08 → 09/10 es semana 9
        assertThat(tarea.getHoraEntrega()).isEqualTo(LocalTime.of(23, 59));
        assertThat(tarea.getHorasEstimadas()).isPositive();              // estimadas automáticamente
        assertThat(tarea.getBloques()).isNotEmpty()                      // el plan ya le reservó estudio
                .allMatch(b -> b.getFecha().isBefore(LocalDate.of(2026, 10, 9)));

        mvc.perform(get("/agenda")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe de robótica")))
                .andExpect(content().string(containsString("23:59")));
        mvc.perform(get("/agenda"))
                .andExpect(content().string(containsString("Semana 9")));
        mvc.perform(get("/api/eventos"))
                .andExpect(content().string(containsString("Control Inteligente · Informe de robótica · 23:59")));
    }

    @Test
    void marcarComoEntregadaQuitaSusBloquesPendientes() throws Exception {
        Curso control = buscar("MT5307");
        mvc.perform(post("/agenda").param("cursoId", control.getId().toString()).param("nombre", "Quiz sorpresa")
                .param("tipo", "PRACTICA").param("fecha", "2026-10-12").param("horasEstimadas", "3"));
        Evaluacion quiz = tarea(control, "Quiz sorpresa");
        assertThat(quiz.getBloques()).isNotEmpty();

        mvc.perform(post("/agenda/" + quiz.getId() + "/completar")).andExpect(redirectedUrl("/agenda"));

        assertThat(quiz.isCompletada()).isTrue();
        assertThat(quiz.getBloques()).allMatch(BloqueEstudio::isCompletado); // ya no quedan bloques pendientes
    }

    @Test
    void editarCambiaLaFechaYEliminarLaQuita() throws Exception {
        Curso control = buscar("MT5307");
        mvc.perform(post("/agenda").param("cursoId", control.getId().toString()).param("nombre", "Exposición")
                .param("tipo", "TAREA").param("fecha", "2026-10-14"));
        Evaluacion exposicion = tarea(control, "Exposición");

        mvc.perform(get("/agenda/" + exposicion.getId() + "/editar")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Guardar cambios")));
        mvc.perform(post("/agenda/" + exposicion.getId()).param("cursoId", control.getId().toString())
                        .param("nombre", "Exposición final").param("tipo", "PROYECTO").param("fecha", "2026-10-21"))
                .andExpect(redirectedUrl("/agenda"));
        assertThat(exposicion.getNombre()).isEqualTo("Exposición final");
        assertThat(exposicion.getFecha()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(exposicion.getSemana()).isEqualTo(11);

        mvc.perform(post("/agenda/" + exposicion.getId() + "/eliminar")).andExpect(redirectedUrl("/agenda"));
        assertThat(control.getEvaluaciones()).noneMatch(e -> e.getNombre().equals("Exposición final"));
    }

    @Test
    void separaExamenesLaboratoriosYTareas() throws Exception {
        Curso control = buscar("MT5307");
        mvc.perform(post("/agenda").param("cursoId", control.getId().toString()).param("nombre", "Informe grupal")
                .param("tipo", "TAREA").param("fecha", "2026-10-15"));

        mvc.perform(get("/agenda")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Exámenes y prácticas")))
                .andExpect(content().string(containsString("Tareas y proyectos")));
        mvc.perform(get("/agenda").param("ver", "tareas")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe grupal")));
        mvc.perform(get("/agenda").param("ver", "laboratorios")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Informe grupal"))));
    }

    @Test
    void validaLosDatosObligatorios() throws Exception {
        mvc.perform(post("/agenda").param("nombre", "").param("tipo", "TAREA"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe qué tienes que entregar")))
                .andExpect(content().string(containsString("Elige el curso")));
    }

    @Test
    void alSubirOtraVezElSilaboSeConservanLosPendientesDeLaAgenda() throws Exception {
        Curso control = buscar("MT5307");
        mvc.perform(post("/agenda").param("cursoId", control.getId().toString()).param("nombre", "Tarea extra")
                .param("tipo", "TAREA").param("fecha", "2026-10-15"));

        mvc.perform(post("/silabos/guardar")
                        .param("codigo", "MT5307").param("nombre", "Control Inteligente")
                        .param("creditos", "4").param("dificultad", "4").param("color", "#7B5EA7")
                        .param("evaluaciones[0].nombre", "EC1").param("evaluaciones[0].tipo", "EXAMEN")
                        .param("evaluaciones[0].semana", "12").param("evaluaciones[0].dia", "MONDAY")
                        .param("evaluaciones[0].pesoEnNotaFinal", "100").param("evaluaciones[0].horasEstimadas", "10"))
                .andExpect(redirectedUrl("/cursos"));

        Curso nuevo = buscar("MT5307");
        assertThat(nuevo.getEvaluaciones()).extracting(Evaluacion::getNombre).contains("EC1", "Tarea extra");
        assertThat(tarea(nuevo, "Tarea extra").getOrigen()).isEqualTo(OrigenEvaluacion.AGENDA);
    }

    private Curso buscar(String codigo) {
        return cursoService.listarCursos().stream().filter(c -> c.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    private Evaluacion tarea(Curso curso, String nombre) {
        return curso.getEvaluaciones().stream().filter(e -> e.getNombre().equals(nombre)).findFirst().orElseThrow();
    }
}

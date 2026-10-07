package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.EventoCalendarioDto;
import pe.utec.planificador.model.*;
import pe.utec.planificador.repository.BloqueEstudioRepository;
import pe.utec.planificador.repository.EvaluacionRepository;
import pe.utec.planificador.repository.HorarioClaseRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Convierte evaluaciones, bloques de estudio y clases en eventos de FullCalendar. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarioEventosService {

    /** Desde cuántos días antes una entrega se marca en rojo. */
    static final int DIAS_URGENTE = 3;
    static final String COLOR_URGENTE = "#C0392B";

    private final EstudianteService estudianteService;
    private final EvaluacionRepository evaluacionRepository;
    private final BloqueEstudioRepository bloqueRepository;
    private final HorarioClaseRepository horarioRepository;
    private final CalendarioAcademicoService calendarioAcademico;
    private final java.time.Clock reloj;

    public List<EventoCalendarioDto> listarEventos() {
        Estudiante estudiante = estudianteService.obtenerActual();
        List<EventoCalendarioDto> eventos = new ArrayList<>();
        evaluacionRepository.findByCursoEstudianteIdOrderByFechaAsc(estudiante.getId())
                .forEach(e -> eventos.add(deEvaluacion(e)));
        bloqueRepository.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(estudiante.getId()).stream()
                .filter(b -> !b.isReprogramado()) // su tiempo ya se movió a otros días
                .forEach(b -> eventos.add(deBloque(b)));
        horarioRepository.findByCursoEstudianteId(estudiante.getId())
                .forEach(h -> eventos.addAll(deHorario(h, estudiante.getFechaInicioCiclo())));
        return eventos;
    }

    /** Entrega en los próximos 3 días que aún no se marcó como entregada. */
    private boolean urgente(Evaluacion e) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy(), e.getFecha());
        return !e.isCompletada() && dias >= 0 && dias <= DIAS_URGENTE;
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private EventoCalendarioDto deEvaluacion(Evaluacion e) {
        Curso curso = e.getCurso();
        String peso = e.getPesoEnNotaFinal() == null || e.getPesoEnNotaFinal() == 0 ? ""
                : String.format(Locale.US, " (%.1f%%)", e.getPesoEnNotaFinal());
        String hora = e.getHoraEntrega() == null ? "" : " · " + e.getHoraEntrega();
        String titulo = (e.isCompletada() ? "✓ " : urgente(e) ? "⚠ " : "") + curso.getNombre() + " · " + e.getNombre() + peso + hora;
        return EventoCalendarioDto.builder()
                .id("evaluacion-" + e.getId())
                .title(titulo)
                .start(e.getFecha().toString())
                .allDay(true)
                .url("/evaluaciones/" + e.getId())
                .color(urgente(e) ? COLOR_URGENTE : curso.getColor())
                .classNames(e.isCompletada() ? List.of("evento-evaluacion", "evento-completado")
                        : urgente(e) ? List.of("evento-evaluacion", "evento-urgente") : List.of("evento-evaluacion"))
                .build();
    }

    private EventoCalendarioDto deBloque(BloqueEstudio b) {
        Evaluacion e = b.getEvaluacion();
        return EventoCalendarioDto.builder()
                .id("bloque-" + b.getId())
                .title((b.isCompletado() ? "✓ " : "") + e.getCurso().getNombre() + " · " + b.getDescripcion())
                .start(LocalDateTime.of(b.getFecha(), b.getHoraInicio()).toString())
                .end(LocalDateTime.of(b.getFecha(), b.getHoraFin()).toString())
                .allDay(false)
                .color(e.getCurso().getColor())
                .url("/evaluaciones/" + e.getId())
                .classNames(b.isCompletado() ? List.of("evento-bloque", "evento-completado")
                        : urgente(e) ? List.of("evento-bloque", "bloque-urgente") : List.of("evento-bloque"))
                .build();
    }

    /** Clases semanales como fondo sombreado durante las 16 semanas del ciclo. */
    private List<EventoCalendarioDto> deHorario(HorarioClase h, LocalDate inicioCiclo) {
        if (h.getFrecuencia().esQuincenal()) {
            return deHorarioQuincenal(h, inicioCiclo);
        }
        return List.of(EventoCalendarioDto.builder()
                .id("clase-" + h.getId())
                .title("Clase " + h.getCurso().getNombre())
                .daysOfWeek(List.of(h.getDiaSemana().getValue() % 7))
                .startTime(h.getHoraInicio().toString())
                .endTime(h.getHoraFin().toString())
                .startRecur(inicioCiclo.toString())
                .endRecur(inicioCiclo.plusWeeks(CalendarioAcademicoService.TOTAL_SEMANAS).toString())
                .color(h.getCurso().getColor())
                .display("background")
                .classNames(List.of("evento-clase"))
                .build());
    }

    /** Una clase "Semana A/B" se dibuja solo en las semanas que le tocan. */
    private List<EventoCalendarioDto> deHorarioQuincenal(HorarioClase h, LocalDate inicioCiclo) {
        List<EventoCalendarioDto> eventos = new ArrayList<>();
        for (int semana = 1; semana <= CalendarioAcademicoService.TOTAL_SEMANAS; semana++) {
            if (!h.aplicaEnSemana(semana)) {
                continue;
            }
            LocalDate fecha = calendarioAcademico.fechaDeSemana(inicioCiclo, semana, h.getDiaSemana());
            eventos.add(EventoCalendarioDto.builder()
                    .id("clase-" + h.getId() + "-s" + semana)
                    .title("Clase " + h.getCurso().getNombre())
                    .start(LocalDateTime.of(fecha, h.getHoraInicio()).toString())
                    .end(LocalDateTime.of(fecha, h.getHoraFin()).toString())
                    .color(h.getCurso().getColor())
                    .display("background")
                    .classNames(List.of("evento-clase"))
                    .build());
        }
        return eventos;
    }
}

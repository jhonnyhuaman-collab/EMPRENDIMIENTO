package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.EventoCalendarioDto;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.repository.HorarioClaseRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Arma la vista "Mi horario": las clases de la semana, cada curso con su color. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HorarioSemanalService {

    private final EstudianteService estudianteService;
    private final HorarioClaseRepository horarioRepository;
    private final CursoService cursoService;
    private final CalendarioAcademicoService calendarioAcademico;

    /**
     * Eventos de una semana tipo para FullCalendar (sin fechas: se repiten cada semana).
     * @param semana "A" (impares), "B" (pares) o vacío para ver todas las clases
     */
    public List<EventoCalendarioDto> eventos(String semana) {
        int numeroSemana = "B".equalsIgnoreCase(semana) ? 2 : 1;
        boolean filtrar = "A".equalsIgnoreCase(semana) || "B".equalsIgnoreCase(semana);
        return clases().stream()
                .filter(h -> !filtrar || h.aplicaEnSemana(numeroSemana))
                .map(h -> EventoCalendarioDto.builder()
                        .id("clase-" + h.getId())
                        .title(h.getCurso().getNombre())
                        .daysOfWeek(List.of(h.getDiaSemana().getValue() % 7))
                        .startTime(h.getHoraInicio().toString())
                        .endTime(h.getHoraFin().toString())
                        .color(h.getCurso().getColor())
                        .classNames(List.of("evento-clase-horario"))
                        .extendedProps(detalle(h).isEmpty() ? null : Map.of("detalle", detalle(h)))
                        .build())
                .toList();
    }

    /** Aula/tipo y, si es quincenal, "Sem A" o "Sem B". */
    private String detalle(HorarioClase h) {
        String texto = h.getDetalle() == null ? "" : h.getDetalle();
        if (h.getFrecuencia().esQuincenal()) {
            texto = texto.isEmpty() ? h.getFrecuencia().getEtiquetaCorta() : texto + " · " + h.getFrecuencia().getEtiquetaCorta();
        }
        return texto;
    }

    /** ¿Hay clases que solo van en Semana A o Semana B? */
    public boolean hayClasesQuincenales() {
        return clases().stream().anyMatch(h -> h.getFrecuencia().esQuincenal());
    }

    /** "A" si la fecha cae en semana impar del ciclo, "B" si cae en semana par. */
    public String semanaDe(LocalDate fecha) {
        int semana = calendarioAcademico.semanaDeFecha(estudianteService.obtenerActual().getFechaInicioCiclo(), fecha);
        return semana % 2 == 0 ? "B" : "A";
    }

    /** Cursos que tienen al menos una clase registrada (para la leyenda de colores). */
    public List<Curso> cursosConHorario() {
        return cursoService.listarCursos().stream().filter(c -> !c.getHorarios().isEmpty()).toList();
    }

    /** Horas de clase por semana. */
    public double horasSemanales() {
        return clases().stream()
                .mapToDouble(h -> Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes() / 60.0)
                .sum();
    }

    private List<HorarioClase> clases() {
        return horarioRepository.findByCursoEstudianteId(estudianteService.obtenerActual().getId());
    }
}

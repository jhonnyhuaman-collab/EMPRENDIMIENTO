package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.CategoriaAgenda;
import pe.utec.planificador.dto.DiaAgendaDto;
import pe.utec.planificador.dto.SeccionAgendaDto;
import pe.utec.planificador.dto.EntregaProximaDto;
import pe.utec.planificador.dto.formulario.PendienteFormulario;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.OrigenEvaluacion;
import pe.utec.planificador.repository.EvaluacionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Agenda: pendientes que no estaban en el sílabo (tareas, quizzes, evaluaciones anunciadas en clase).
 * Se guardan como evaluaciones del curso, así el calendario y el plan de estudio los toman en cuenta.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AgendaService {

    /** Cuántos días hacia adelante muestra la agenda. */
    static final int DIAS_AGENDA = 60;
    /** Pendientes vencidos sin marcar que se siguen mostrando. */
    static final int DIAS_VENCIDAS = 14;
    private static final DateTimeFormatter FORMATO_DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"));

    private final EstudianteService estudianteService;
    private final EvaluacionService evaluacionService;
    private final EvaluacionRepository evaluacionRepository;
    private final CalendarioAcademicoService calendarioAcademico;
    private final EstimadorHorasService estimadorHoras;
    private final PrioridadService prioridadService;
    private final PlanificadorService planificadorService;

    /** Entregas de los próximos días separadas en exámenes, laboratorios y tareas; cada sección agrupada por fecha. */
    @Transactional(readOnly = true)
    public List<SeccionAgendaDto> proximasPorCategoria(LocalDate hoy) {
        List<EntregaProximaDto> proximas = evaluacionService.proximas(hoy, DIAS_AGENDA);
        return Arrays.stream(CategoriaAgenda.values())
                .map(c -> new SeccionAgendaDto(c, agruparPorDia(
                        proximas.stream().filter(e -> c.incluye(e.getEvaluacion().getTipo())).toList(), hoy)))
                .toList();
    }

    private List<DiaAgendaDto> agruparPorDia(List<EntregaProximaDto> entregas, LocalDate hoy) {
        Map<LocalDate, List<EntregaProximaDto>> porDia = new TreeMap<>();
        entregas.forEach(e -> porDia.computeIfAbsent(e.getEvaluacion().getFecha(), f -> new ArrayList<>()).add(e));
        LocalDate inicio = estudianteService.obtenerActual().getFechaInicioCiclo();
        return porDia.entrySet().stream()
                .map(d -> new DiaAgendaDto(d.getKey(), tituloDelDia(d.getKey(), hoy),
                        calendarioAcademico.semanaDeFecha(inicio, d.getKey()), d.getValue()))
                .toList();
    }

    /** Pendientes de la agenda cuya fecha ya pasó y no se marcaron como entregados. */
    @Transactional(readOnly = true)
    public List<EntregaProximaDto> vencidasSinMarcar(LocalDate hoy) {
        Long estudianteId = estudianteService.obtenerActual().getId();
        return evaluacionRepository
                .findByCursoEstudianteIdAndFechaBetweenOrderByFechaAsc(estudianteId, hoy.minusDays(DIAS_VENCIDAS), hoy.minusDays(1))
                .stream()
                .filter(e -> e.esDeAgenda() && !e.isCompletada())
                .map(e -> new EntregaProximaDto(e, ChronoUnit.DAYS.between(hoy, e.getFecha()), prioridadService.nivel(e, hoy)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PendienteFormulario formularioDe(Long evaluacionId) {
        Evaluacion e = evaluacionService.buscarPorId(evaluacionId);
        PendienteFormulario formulario = new PendienteFormulario();
        formulario.setCursoId(e.getCurso().getId());
        formulario.setNombre(e.getNombre());
        formulario.setTipo(e.getTipo());
        formulario.setFecha(e.getFecha());
        formulario.setHoraEntrega(e.getHoraEntrega());
        formulario.setPesoEnNotaFinal(e.getPesoEnNotaFinal());
        formulario.setHorasEstimadas(e.getHorasEstimadas());
        formulario.setNotas(e.getNotas());
        return formulario;
    }

    public Evaluacion crear(PendienteFormulario formulario, LocalDateTime ahora) {
        Evaluacion evaluacion = Evaluacion.builder().origen(OrigenEvaluacion.AGENDA).build();
        aplicar(formulario, evaluacion);
        planificadorService.generarPlan(ahora);
        return evaluacion;
    }

    /** Sirve también para corregir la fecha de una evaluación del sílabo (p. ej. si el profesor la movió). */
    public Evaluacion actualizar(Long evaluacionId, PendienteFormulario formulario, LocalDateTime ahora) {
        Evaluacion evaluacion = evaluacionService.buscarPorId(evaluacionId);
        aplicar(formulario, evaluacion);
        planificadorService.generarPlan(ahora);
        return evaluacion;
    }

    public String eliminar(Long evaluacionId, LocalDateTime ahora) {
        Evaluacion evaluacion = evaluacionService.buscarPorId(evaluacionId);
        evaluacion.getCurso().getEvaluaciones().remove(evaluacion);
        evaluacionRepository.flush();
        planificadorService.generarPlan(ahora);
        return evaluacion.getNombre();
    }

    /** Marca como entregada (o la reabre) y rehace el plan. */
    public void alternarCompletada(Long evaluacionId, LocalDateTime ahora) {
        Evaluacion evaluacion = evaluacionService.buscarPorId(evaluacionId);
        evaluacion.setCompletada(!evaluacion.isCompletada());
        planificadorService.generarPlan(ahora);
    }

    private void aplicar(PendienteFormulario formulario, Evaluacion evaluacion) {
        Estudiante estudiante = estudianteService.obtenerActual();
        Curso curso = estudiante.getCursos().stream()
                .filter(c -> c.getId().equals(formulario.getCursoId()))
                .findFirst()
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el curso " + formulario.getCursoId()));
        double peso = formulario.getPesoEnNotaFinal() == null ? 0 : formulario.getPesoEnNotaFinal();

        evaluacion.setNombre(formulario.getNombre().strip());
        evaluacion.setTipo(formulario.getTipo());
        evaluacion.setFecha(formulario.getFecha());
        evaluacion.setSemana(Math.max(1, calendarioAcademico.semanaDeFecha(estudiante.getFechaInicioCiclo(), formulario.getFecha())));
        evaluacion.setHoraEntrega(formulario.getHoraEntrega());
        evaluacion.setPesoEnNotaFinal(peso);
        evaluacion.setHorasEstimadas(formulario.getHorasEstimadas() != null ? formulario.getHorasEstimadas()
                : estimadorHoras.estimarHoras(formulario.getTipo(), curso.getDificultad(), peso));
        evaluacion.setNotas(formulario.getNotas() == null || formulario.getNotas().isBlank() ? null : formulario.getNotas().strip());

        if (evaluacion.getCurso() != curso) {
            if (evaluacion.getCurso() != null) {
                evaluacion.getCurso().getEvaluaciones().remove(evaluacion);
            }
            curso.agregarEvaluacion(evaluacion);
        }
        evaluacionRepository.save(evaluacion);
    }

    private String tituloDelDia(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) return "Hoy";
        if (fecha.equals(hoy.plusDays(1))) return "Mañana";
        String texto = fecha.format(FORMATO_DIA);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}

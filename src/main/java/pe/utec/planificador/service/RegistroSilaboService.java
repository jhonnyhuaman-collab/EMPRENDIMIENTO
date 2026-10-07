package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.ResultadoPlanDto;
import pe.utec.planificador.dto.formulario.FilaEvaluacion;
import pe.utec.planificador.dto.formulario.FilaHorario;
import pe.utec.planificador.dto.formulario.SilaboFormulario;
import pe.utec.planificador.model.*;
import pe.utec.planificador.repository.CursoRepository;

import java.time.LocalDateTime;
import java.util.*;

/** Guarda el curso revisado (temas, evaluaciones y horario) y vuelve a generar el plan de estudio. */
@Service
@RequiredArgsConstructor
@Transactional
public class RegistroSilaboService {

    private final EstudianteService estudianteService;
    private final CursoRepository cursoRepository;
    private final CalendarioAcademicoService calendarioAcademico;
    private final PlanificadorService planificadorService;
    private final DiaEvaluacionService diaEvaluacion;

    @Transactional(readOnly = true)
    public boolean existeCurso(String codigo) {
        return codigo != null && !codigo.isBlank() && buscarPorCodigo(estudianteService.obtenerActual(), codigo).isPresent();
    }

    /** Crea el curso (reemplaza al anterior si ya existía uno con el mismo código) y replanifica. */
    public ResultadoPlanDto guardar(SilaboFormulario formulario, LocalDateTime ahora) {
        validarHorarios(formulario);
        Estudiante estudiante = estudianteService.obtenerActual();

        // Si el curso ya existía con horario (importado del consolidado de matrícula), ese horario manda:
        // trae teoría/laboratorio y Semana A/B, que el sílabo normalmente no tiene
        List<HorarioClase> horarioAnterior = new ArrayList<>();
        List<Evaluacion> agendaAnterior = new ArrayList<>();
        String codigoAnterior = formulario.getCodigoExistente() == null || formulario.getCodigoExistente().isBlank()
                ? formulario.getCodigo() : formulario.getCodigoExistente();
        buscarPorCodigo(estudiante, codigoAnterior).ifPresent(anterior -> {
            if (formulario.getHorarios().isEmpty() || !anterior.getHorarios().isEmpty()) {
                anterior.getHorarios().forEach(h -> horarioAnterior.add(HorarioClase.builder()
                        .diaSemana(h.getDiaSemana()).horaInicio(h.getHoraInicio()).horaFin(h.getHoraFin())
                        .detalle(h.getDetalle()).frecuencia(h.getFrecuencia()).build()));
            }
            anterior.getEvaluaciones().stream().filter(Evaluacion::esDeAgenda).forEach(e -> agendaAnterior.add(copiar(e)));
            estudiante.getCursos().remove(anterior);
            cursoRepository.flush();
        });

        Curso curso = Curso.builder()
                .codigo(formulario.getCodigo().strip().toUpperCase())
                .nombre(formulario.getNombre().strip())
                .creditos(formulario.getCreditos())
                .dificultad(formulario.getDificultad())
                .color(formulario.getColor())
                .build();
        Map<String, Tema> temasPorNumero = agregarTemas(curso, formulario);
        if (horarioAnterior.isEmpty()) {
            formulario.getHorarios().forEach(h -> curso.agregarHorario(HorarioClase.builder()
                    .diaSemana(h.getDia()).horaInicio(h.getHoraInicio()).horaFin(h.getHoraFin()).build()));
        }
        horarioAnterior.forEach(curso::agregarHorario);
        agendaAnterior.forEach(curso::agregarEvaluacion);
        formulario.getEvaluaciones().forEach(fila -> {
            Evaluacion evaluacion = crearEvaluacion(fila, estudiante, temasPorNumero);
            // Se rinde a la hora de la clase de ese día (laboratorio o teoría)
            diaEvaluacion.horaDeClase(fila.getTipo(), fila.getDia(), curso.getHorarios()).ifPresent(evaluacion::setHoraEntrega);
            curso.agregarEvaluacion(evaluacion);
        });

        estudiante.agregarCurso(curso);
        cursoRepository.save(curso);
        return planificadorService.generarPlan(ahora);
    }

    private Map<String, Tema> agregarTemas(Curso curso, SilaboFormulario formulario) {
        Map<String, Tema> porNumero = new LinkedHashMap<>();
        formulario.getTemas().forEach(fila -> {
            Tema tema = Tema.builder()
                    .unidad(fila.getUnidad()).numero(fila.getNumero().strip()).titulo(fila.getTitulo().strip())
                    .build();
            curso.agregarTema(tema);
            porNumero.putIfAbsent(tema.getNumero(), tema);
        });
        return porNumero;
    }

    private Evaluacion crearEvaluacion(FilaEvaluacion fila, Estudiante estudiante, Map<String, Tema> temasPorNumero) {
        List<Tema> temas = new ArrayList<>();
        if (fila.getTemas() != null) {
            Arrays.stream(fila.getTemas().split("[,;\\s]+"))
                    .map(String::strip)
                    .filter(temasPorNumero::containsKey)
                    .distinct()
                    .forEach(numero -> temas.add(temasPorNumero.get(numero)));
        }
        return Evaluacion.builder()
                .nombre(fila.getNombre().strip())
                .tipo(fila.getTipo())
                .semana(fila.getSemana())
                .fecha(calendarioAcademico.fechaDeSemana(estudiante.getFechaInicioCiclo(), fila.getSemana(), fila.getDia()))
                .pesoEnNotaFinal(fila.getPesoEnNotaFinal())
                .horasEstimadas(fila.getHorasEstimadas())
                .temas(temas)
                .build();
    }

    /** Copia un pendiente de la agenda (con lo ya estudiado) para pasarlo al curso nuevo. */
    private Evaluacion copiar(Evaluacion e) {
        Evaluacion copia = Evaluacion.builder()
                .nombre(e.getNombre()).tipo(e.getTipo()).semana(e.getSemana()).fecha(e.getFecha())
                .pesoEnNotaFinal(e.getPesoEnNotaFinal()).horasEstimadas(e.getHorasEstimadas()).nota(e.getNota())
                .origen(e.getOrigen()).horaEntrega(e.getHoraEntrega()).completada(e.isCompletada()).notas(e.getNotas())
                .build();
        e.getBloques().stream().filter(BloqueEstudio::isCompletado).forEach(b -> copia.agregarBloque(BloqueEstudio.builder()
                .fecha(b.getFecha()).horaInicio(b.getHoraInicio()).horaFin(b.getHoraFin())
                .descripcion(b.getDescripcion()).completado(true).build()));
        return copia;
    }

    private void validarHorarios(SilaboFormulario formulario) {
        for (FilaHorario h : formulario.getHorarios()) {
            if (!h.getHoraFin().isAfter(h.getHoraInicio())) {
                throw new SilaboException("En el horario de clases, la hora de fin debe ser posterior a la de inicio.");
            }
        }
    }

    private Optional<Curso> buscarPorCodigo(Estudiante estudiante, String codigo) {
        return estudiante.getCursos().stream()
                .filter(c -> c.getCodigo().equalsIgnoreCase(codigo.strip()))
                .findFirst();
    }
}

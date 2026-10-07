package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.ResultadoHorarioDto;
import pe.utec.planificador.dto.formulario.FilaCursoHorario;
import pe.utec.planificador.dto.formulario.FilaSesion;
import pe.utec.planificador.dto.formulario.HorarioFormulario;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.repository.CursoRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Guarda el horario revisado: crea los cursos nuevos y, en los que ya existen, reemplaza su horario
 * conservando sus evaluaciones (que se mueven al día y hora de su clase). Luego replanifica para no poner estudio encima de las clases.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegistroHorarioService {

    /** Dificultad inicial de un curso que solo viene del horario (se ajusta al subir su sílabo). */
    private static final int DIFICULTAD_INICIAL = 3;

    private final EstudianteService estudianteService;
    private final CursoRepository cursoRepository;
    private final PlanificadorService planificadorService;
    private final DiaEvaluacionService diaEvaluacion;

    public ResultadoHorarioDto guardar(HorarioFormulario formulario, LocalDateTime ahora) {
        Map<String, FilaCursoHorario> cursosDelFormulario = validar(formulario);
        Estudiante estudiante = estudianteService.obtenerActual();
        Map<String, Curso> existentes = new HashMap<>();
        estudiante.getCursos().forEach(c -> existentes.put(clave(c.getCodigo()), c));

        int nuevos = 0;
        int actualizados = 0;
        for (FilaCursoHorario fila : cursosDelFormulario.values()) {
            Curso curso = existentes.get(clave(fila.getCodigo()));
            if (curso == null) {
                curso = Curso.builder()
                        .codigo(clave(fila.getCodigo()))
                        .dificultad(DIFICULTAD_INICIAL)
                        .build();
                estudiante.agregarCurso(curso);
                nuevos++;
            } else {
                curso.getHorarios().clear();
                actualizados++;
            }
            curso.setNombre(fila.getNombre().strip());
            curso.setColor(fila.getColor());
            if (fila.getCreditos() > 0 || curso.getCreditos() == null) {
                curso.setCreditos(fila.getCreditos());
            }
            for (FilaSesion sesion : formulario.getSesiones()) {
                if (clave(sesion.getCodigoCurso()).equals(clave(fila.getCodigo()))) {
                    curso.agregarHorario(HorarioClase.builder()
                            .diaSemana(sesion.getDia())
                            .horaInicio(sesion.getHoraInicio())
                            .horaFin(sesion.getHoraFin())
                            .frecuencia(sesion.getFrecuencia())
                            .detalle(sesion.getDetalle() == null || sesion.getDetalle().isBlank() ? null : sesion.getDetalle().strip())
                            .build());
                }
            }
        }
        // Con el horario nuevo, las evaluaciones del sílabo pasan al día y hora de su clase
        estudiante.getCursos().forEach(c -> diaEvaluacion.reubicar(c, estudiante.getFechaInicioCiclo()));
        cursoRepository.flush();
        planificadorService.generarPlan(ahora);
        return new ResultadoHorarioDto(nuevos, actualizados, formulario.getSesiones().size());
    }

    /** Revisa códigos repetidos, sesiones sin curso y horas invertidas. */
    private Map<String, FilaCursoHorario> validar(HorarioFormulario formulario) {
        Map<String, FilaCursoHorario> porCodigo = new LinkedHashMap<>();
        for (FilaCursoHorario fila : formulario.getCursos()) {
            if (porCodigo.put(clave(fila.getCodigo()), fila) != null) {
                throw new SilaboException("El código " + clave(fila.getCodigo()) + " está repetido en la lista de cursos.");
            }
        }
        for (FilaSesion sesion : formulario.getSesiones()) {
            if (!porCodigo.containsKey(clave(sesion.getCodigoCurso()))) {
                throw new SilaboException("Hay una clase del curso \"" + sesion.getCodigoCurso()
                        + "\", pero ese código no está en la lista de cursos.");
            }
            if (!sesion.getHoraFin().isAfter(sesion.getHoraInicio())) {
                throw new SilaboException("En la clase de " + clave(sesion.getCodigoCurso())
                        + ", la hora de fin debe ser posterior a la de inicio.");
            }
        }
        return porCodigo;
    }

    private String clave(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }
}

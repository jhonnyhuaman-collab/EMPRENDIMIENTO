package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.ResultadoPlanDto;
import pe.utec.planificador.dto.formulario.ConfiguracionFormulario;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.model.Evaluacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;

/**
 * Configuración del estudiante. Al guardar, cada evaluación del sílabo conserva su semana y se ubica
 * en el día y hora de su clase (laboratorio o teoría); luego se rehace el plan de estudio.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConfiguracionService {

    private final EstudianteService estudianteService;
    private final CalendarioAcademicoService calendarioAcademico;
    private final PlanificadorService planificadorService;
    private final DiaEvaluacionService diaEvaluacion;

    @Transactional(readOnly = true)
    public ConfiguracionFormulario obtener() {
        Estudiante estudiante = estudianteService.obtenerActual();
        ConfiguracionFormulario formulario = new ConfiguracionFormulario();
        formulario.setNombre(estudiante.getNombre());
        formulario.setFechaInicioCiclo(estudiante.getFechaInicioCiclo());
        formulario.setHorasEstudioPorDia(estudiante.getHorasEstudioPorDia());
        formulario.setHoraDormir(estudiante.getHoraDormir());
        formulario.setCargaMaximaDiaria(estudiante.getCargaMaximaDiariaEfectiva());
        formulario.setHorasMaximasCursoSemana(estudiante.getHorasMaximasCursoSemanaEfectivas());
        formulario.setDiasEstudio(new ArrayList<>(estudiante.getDiasEstudioSet()));
        return formulario;
    }

    public ResultadoPlanDto guardar(ConfiguracionFormulario formulario, LocalDateTime ahora) {
        Estudiante estudiante = estudianteService.obtenerActual();
        // La semana 1 siempre empieza en lunes
        if (formulario.getNombre() != null && !formulario.getNombre().isBlank()) {
            estudiante.setNombre(formulario.getNombre().strip());
        }
        LocalDate inicio = formulario.getFechaInicioCiclo().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        if (!inicio.equals(estudiante.getFechaInicioCiclo())) {
            estudiante.getCursos().forEach(curso -> curso.getEvaluaciones().stream().filter(Evaluacion::esDeAgenda)
                    .forEach(e -> e.setSemana(Math.max(1, calendarioAcademico.semanaDeFecha(inicio, e.getFecha())))));
        }
        // Las evaluaciones del sílabo se ubican en su semana, en el día y hora de su clase
        estudiante.getCursos().forEach(curso -> diaEvaluacion.reubicar(curso, inicio));
        estudiante.setFechaInicioCiclo(inicio);
        estudiante.setHorasEstudioPorDia(formulario.getHorasEstudioPorDia());
        estudiante.setHoraDormir(formulario.getHoraDormir());
        estudiante.setCargaMaximaDiaria(formulario.getCargaMaximaDiaria());
        estudiante.setHorasMaximasCursoSemana(formulario.getHorasMaximasCursoSemana());
        estudiante.setDiasEstudioSet(formulario.getDiasEstudio());
        return planificadorService.generarPlan(ahora);
    }

    /** Semana actual del ciclo según la configuración guardada. */
    @Transactional(readOnly = true)
    public int semanaActual(LocalDate hoy) {
        return calendarioAcademico.semanaDeFecha(estudianteService.obtenerActual().getFechaInicioCiclo(), hoy);
    }
}

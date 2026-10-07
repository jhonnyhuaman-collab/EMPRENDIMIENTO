package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.model.TipoEvaluacion;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ubica cada evaluación del sílabo en una clase del horario del curso:
 * laboratorios en la sesión de laboratorio, exámenes, prácticas y entregas en la sesión de teoría.
 * La semana la dice el sílabo; el día y la hora salen del horario (consolidado de matrícula).
 */
@Service
@RequiredArgsConstructor
public class DiaEvaluacionService {

    private final CalendarioAcademicoService calendarioAcademico;

    public DayOfWeek elegirDia(TipoEvaluacion tipo, int semana, List<HorarioClase> sesiones) {
        return elegirSesion(tipo, semana, sesiones).map(HorarioClase::getDiaSemana).orElse(DayOfWeek.MONDAY);
    }

    /**
     * Sesión en que se rinde la evaluación. Primero se buscan las clases que hay esa semana (Semana A / B);
     * si el laboratorio no cae esa semana, igual se usa el día de laboratorio (el sílabo manda en la semana).
     */
    public Optional<HorarioClase> elegirSesion(TipoEvaluacion tipo, int semana, List<HorarioClase> sesiones) {
        if (sesiones.isEmpty()) {
            return Optional.empty();
        }
        List<HorarioClase> deEsaSemana = sesiones.stream().filter(s -> s.aplicaEnSemana(semana)).toList();
        Optional<HorarioClase> preferida = tipo == TipoEvaluacion.LABORATORIO
                ? buscar(deEsaSemana, true).or(() -> buscar(sesiones, true))
                : buscar(deEsaSemana, false).or(() -> buscar(sesiones, false));
        return preferida.or(() -> primera(deEsaSemana)).or(() -> primera(sesiones));
    }

    /**
     * Vuelve a ubicar las evaluaciones del sílabo del curso según su horario actual (día y hora de clase).
     * Los pendientes de la agenda no se tocan: su fecha la puso el estudiante.
     */
    public void reubicar(Curso curso, LocalDate inicioCiclo) {
        for (Evaluacion e : curso.getEvaluaciones()) {
            if (e.esDeAgenda() || e.getSemana() == null) {
                continue;
            }
            Optional<HorarioClase> sesion = elegirSesion(e.getTipo(), e.getSemana(), curso.getHorarios());
            DayOfWeek dia = sesion.map(HorarioClase::getDiaSemana)
                    .orElse(e.getFecha() == null ? DayOfWeek.MONDAY : e.getFecha().getDayOfWeek());
            e.setFecha(calendarioAcademico.fechaDeSemana(inicioCiclo, e.getSemana(), dia));
            sesion.ifPresent(s -> e.setHoraEntrega(s.getHoraInicio()));
        }
    }

    /** Hora de la clase del curso ese día (la de laboratorio para los laboratorios), si hay clase. */
    public Optional<LocalTime> horaDeClase(TipoEvaluacion tipo, DayOfWeek dia, List<HorarioClase> sesiones) {
        List<HorarioClase> delDia = sesiones.stream().filter(s -> s.getDiaSemana() == dia).toList();
        return buscar(delDia, tipo == TipoEvaluacion.LABORATORIO).or(() -> primera(delDia)).map(HorarioClase::getHoraInicio);
    }

    /** Sesiones de laboratorio (o las que no son de laboratorio), la primera de la semana. */
    private Optional<HorarioClase> buscar(List<HorarioClase> sesiones, boolean laboratorio) {
        return sesiones.stream().filter(s -> esLaboratorio(s) == laboratorio)
                .min(Comparator.comparing(HorarioClase::getDiaSemana).thenComparing(HorarioClase::getHoraInicio));
    }

    private Optional<HorarioClase> primera(List<HorarioClase> sesiones) {
        return sesiones.stream().min(Comparator.comparing(HorarioClase::getDiaSemana).thenComparing(HorarioClase::getHoraInicio));
    }

    private boolean esLaboratorio(HorarioClase sesion) {
        String detalle = sesion.getDetalle() == null ? ""
                : Normalizer.normalize(sesion.getDetalle(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return detalle.contains("lab");
    }
}

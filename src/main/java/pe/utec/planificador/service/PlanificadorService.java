package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.IntervaloHorario;
import pe.utec.planificador.dto.PasoRepasoDto;
import pe.utec.planificador.dto.ResultadoPlanDto;
import pe.utec.planificador.model.*;
import pe.utec.planificador.repository.EvaluacionRepository;
import pe.utec.planificador.repository.HorarioClaseRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Genera el plan de estudio diario.
 * <ul>
 *   <li>Reparte las horas de cada evaluación en los días previos (nunca todo el último día).</li>
 *   <li>No supera las horas de estudio por día del estudiante.</li>
 *   <li>No pasa de las horas por curso a la semana (un curso no llena toda la semana).</li>
 *   <li>No choca con el horario de clases ni pasa la hora de dormir.</li>
 *   <li>Si dos evaluaciones compiten por el mismo día, va primero la de mayor prioridad.</li>
 * </ul>
 * Los bloques completados se respetan; los pendientes desde hoy se vuelven a calcular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlanificadorService {

    private static final int MINUTOS_MAXIMOS_POR_BLOQUE = 90;
    private static final double MEDIA_HORA = 0.5;
    /** En la primera pasada del día, cada evaluación recibe como máximo esto. */
    private static final double HORAS_MINIMAS_POR_PASADA = 1.0;
    /** Un día lleno de clases igual recibe un repaso corto si hay un hueco libre (ningún día de estudio queda vacío). */
    private static final double REPASO_MINIMO_DIA_CARGADO = 1.0;

    private final EstudianteService estudianteService;
    private final EvaluacionRepository evaluacionRepository;
    private final HorarioClaseRepository horarioRepository;
    private final PrioridadService prioridadService;
    private final VentanaPreparacionService ventanaPreparacion;
    private final DisponibilidadService disponibilidad;
    private final PlanRepasoService planRepaso;
    private final CalendarioAcademicoService calendarioAcademico;

    public ResultadoPlanDto generarPlan(LocalDateTime ahora) {
        Estudiante estudiante = estudianteService.obtenerActual();
        LocalDate hoy = ahora.toLocalDate();
        List<Evaluacion> evaluaciones = evaluacionRepository.findByCursoEstudianteIdOrderByFechaAsc(estudiante.getId());
        List<HorarioClase> clases = horarioRepository.findByCursoEstudianteId(estudiante.getId());

        borrarBloquesPendientes(evaluaciones, hoy);
        Map<LocalDate, List<BloqueEstudio>> ocupadosPorDia = bloquesConservadosPorDia(evaluaciones, hoy);
        Set<DayOfWeek> diasEstudio = estudiante.getDiasEstudioSet();
        Map<Long, Map<LocalDate, Double>> horasPorCursoSemana = horasYaPlanificadasPorCursoSemana(evaluaciones, hoy);
        List<Pendiente> pendientes = evaluaciones.stream()
                .filter(e -> e.getFecha().isAfter(hoy) && !e.isCompletada())
                .map(e -> crearPendiente(e, diasEstudio))
                .filter(p -> p.restante >= MEDIA_HORA)
                .toList();

        int bloquesCreados = 0;
        double horasPlanificadas = 0;
        LocalDate ultimoDia = pendientes.stream().map(p -> p.fechaLimite).max(Comparator.naturalOrder()).orElse(hoy);

        for (LocalDate dia = hoy; dia.isBefore(ultimoDia); dia = dia.plusDays(1)) {
            if (!diasEstudio.contains(dia.getDayOfWeek())) {
                continue; // fin de semana u otro día libre
            }
            int semana = calendarioAcademico.semanaDeFecha(estudiante.getFechaInicioCiclo(), dia);
            List<HorarioClase> clasesDelDia = clases.stream().filter(c -> c.aplicaEnSemana(semana)).toList();
            List<BloqueEstudio> ocupados = ocupadosPorDia.computeIfAbsent(dia, d -> new ArrayList<>());
            double capacidad = capacidadDelDia(estudiante, dia, clasesDelDia) - sumarHoras(ocupados);
            LocalTime desde = dia.equals(hoy) ? siguienteMediaHora(ahora.toLocalTime()) : null;
            if (desde == null && dia.equals(hoy)) {
                continue; // ya es muy tarde para estudiar hoy
            }

            LocalDate lunes = lunesDe(dia);
            List<Pendiente> candidatos = candidatosDelDia(pendientes, dia);
            Map<Long, Double> saldoPorCurso = new HashMap<>();
            candidatos.forEach(p -> saldoPorCurso.put(cursoId(p), estudiante.getHorasMaximasCursoSemanaEfectivas()
                    - horasPorCursoSemana.getOrDefault(cursoId(p), Map.of()).getOrDefault(lunes, 0.0)));
            Map<Pendiente, Double> horasDelDia = repartirCapacidad(candidatos, dia, capacidad, diasEstudio, saldoPorCurso);
            for (Pendiente p : candidatos) {
                double horas = horasDelDia.getOrDefault(p, 0.0);
                if (horas < MEDIA_HORA) {
                    continue;
                }
                int antes = ocupados.size();
                double asignadas = colocarBloques(p, dia, horas, desde, estudiante.getHoraDormir(), clasesDelDia, ocupados);
                bloquesCreados += ocupados.size() - antes;
                horasPlanificadas += asignadas;
                p.restante -= asignadas;
                horasPorCursoSemana.computeIfAbsent(cursoId(p), c -> new HashMap<>()).merge(lunes, asignadas, Double::sum);
            }
        }

        List<String> advertencias = pendientes.stream()
                .filter(p -> p.restante >= MEDIA_HORA)
                .map(p -> String.format(Locale.US,
                        "No alcanzó el tiempo para %s · %s: faltan %.1f h. En Configuración puedes subir las horas por día o por curso a la semana, o activar el sábado.",
                        p.evaluacion.getCurso().getNombre(), p.evaluacion.getNombre(), p.restante))
                .toList();
        log.info("Plan generado: {} bloques, {} h, {} advertencias", bloquesCreados, horasPlanificadas, advertencias.size());
        return new ResultadoPlanDto(bloquesCreados, horasPlanificadas, advertencias);
    }

    // ---------- Preparación ----------

    /** Quita los bloques no completados desde hoy en adelante: se recalculan. */
    private void borrarBloquesPendientes(List<Evaluacion> evaluaciones, LocalDate hoy) {
        evaluaciones.forEach(e -> e.getBloques().removeIf(b -> !b.isCompletado() && !b.getFecha().isBefore(hoy)));
    }

    private Map<LocalDate, List<BloqueEstudio>> bloquesConservadosPorDia(List<Evaluacion> evaluaciones, LocalDate hoy) {
        Map<LocalDate, List<BloqueEstudio>> porDia = new HashMap<>();
        evaluaciones.stream()
                .flatMap(e -> e.getBloques().stream())
                .filter(b -> !b.getFecha().isBefore(hoy))
                .forEach(b -> porDia.computeIfAbsent(b.getFecha(), d -> new ArrayList<>()).add(b));
        return porDia;
    }

    /** Horas de estudio que ya tiene cada curso por semana (bloques conservados desde el lunes de esta semana). */
    private Map<Long, Map<LocalDate, Double>> horasYaPlanificadasPorCursoSemana(List<Evaluacion> evaluaciones, LocalDate hoy) {
        LocalDate lunes = lunesDe(hoy);
        Map<Long, Map<LocalDate, Double>> horas = new HashMap<>();
        for (Evaluacion e : evaluaciones) {
            e.getBloques().stream()
                    .filter(b -> !b.isReprogramado() && !b.getFecha().isBefore(lunes))
                    .forEach(b -> horas.computeIfAbsent(e.getCurso().getId(), c -> new HashMap<>())
                            .merge(lunesDe(b.getFecha()), b.getDuracionHoras(), Double::sum));
        }
        return horas;
    }

    /**
     * Horas de estudio posibles en el día: no más de las horas de estudio por día del estudiante,
     * y sin que clases + estudio pasen de la carga máxima diaria (un día con 6 h de clase deja 3 h de estudio).
     * Si las clases ya llenan el día, queda un repaso corto de 1 h.
     */
    double capacidadDelDia(Estudiante estudiante, LocalDate dia, List<HorarioClase> clasesDelDia) {
        double horasClase = horasDeClase(dia, clasesDelDia);
        double libre = Math.max(0, estudiante.getCargaMaximaDiariaEfectiva() - horasClase);
        double tope = Math.max(libre, REPASO_MINIMO_DIA_CARGADO);
        return redondearAbajo(Math.min(estudiante.getHorasEstudioPorDia(), tope));
    }

    /** Horas de clase del día; si dos clases se cruzan, el tramo común cuenta una sola vez. */
    private double horasDeClase(LocalDate dia, List<HorarioClase> clasesDelDia) {
        List<HorarioClase> delDia = clasesDelDia.stream()
                .filter(c -> c.getDiaSemana() == dia.getDayOfWeek())
                .sorted(Comparator.comparing(HorarioClase::getHoraInicio))
                .toList();
        long minutos = 0;
        LocalTime finCubierto = LocalTime.MIN;
        for (HorarioClase c : delDia) {
            LocalTime inicio = c.getHoraInicio().isAfter(finCubierto) ? c.getHoraInicio() : finCubierto;
            if (c.getHoraFin().isAfter(inicio)) {
                minutos += ChronoUnit.MINUTES.between(inicio, c.getHoraFin());
                finCubierto = c.getHoraFin();
            }
        }
        return minutos / 60.0;
    }

    private Pendiente crearPendiente(Evaluacion evaluacion, Set<DayOfWeek> diasEstudio) {
        double completadas = evaluacion.getBloques().stream()
                .filter(BloqueEstudio::isCompletado)
                .mapToDouble(BloqueEstudio::getDuracionHoras)
                .sum();
        double estimadas = evaluacion.getHorasEstimadas() == null ? 0 : evaluacion.getHorasEstimadas();
        return new Pendiente(evaluacion,
                ventanaPreparacion.inicioRecomendado(evaluacion, diasEstudio),
                ventanaPreparacion.maximoHorasPorDia(evaluacion),
                planRepaso.armarPlan(evaluacion),
                Math.max(0, estimadas - completadas),
                completadas);
    }

    // ---------- Reparto por día ----------

    /** Evaluaciones que se pueden estudiar ese día, de mayor a menor prioridad. */
    private List<Pendiente> candidatosDelDia(List<Pendiente> pendientes, LocalDate dia) {
        return pendientes.stream()
                .filter(p -> p.restante >= MEDIA_HORA)
                .filter(p -> !dia.isBefore(p.inicioVentana) && dia.isBefore(p.fechaLimite))
                .sorted(Comparator.comparingDouble((Pendiente p) -> prioridadService.puntaje(p.evaluacion, dia)).reversed())
                .toList();
    }

    /**
     * Decide cuántas horas recibe cada evaluación en el día.
     * Pasada 1: sesiones de 1 h. Pasada 2: 30 min a los cursos sin nada ese día. Pasada 3: el resto de su cuota, por prioridad.
     * Nunca pasa del saldo semanal de su curso.
     */
    private Map<Pendiente, Double> repartirCapacidad(List<Pendiente> candidatos, LocalDate dia, double capacidad,
                                                     Set<DayOfWeek> diasEstudio, Map<Long, Double> saldoPorCurso) {
        Map<Pendiente, Double> cuotas = new HashMap<>();
        Map<Pendiente, Double> asignadas = new HashMap<>();
        for (Pendiente p : candidatos) {
            // Al menos 1 h por sesión: bloques de 30 min sueltos no rinden
            cuotas.put(p, Math.max(cuotaDelDia(p, dia, diasEstudio), Math.min(HORAS_MINIMAS_POR_PASADA, p.restante)));
        }
        double libre = capacidad;
        // Pasada 1: sesiones de 1 h por prioridad. Pasada 2: 30 min a los cursos que se quedaron sin nada ese día.
        // Pasada 3: el resto de la cuota.
        double[] topes = {HORAS_MINIMAS_POR_PASADA, MEDIA_HORA, Double.MAX_VALUE};
        for (int pasada = 0; pasada < topes.length; pasada++) {
            for (Pendiente p : candidatos) {
                if (pasada == 1 && asignadas.keySet().stream().anyMatch(a -> Objects.equals(cursoId(a), cursoId(p)))) {
                    continue;
                }
                double saldo = saldoPorCurso.get(cursoId(p));
                double horas = redondearAbajo(Math.min(Math.min(Math.min(cuotas.get(p), topes[pasada]), libre), saldo));
                // Una sesión nueva dura al menos 1 h (salvo que sea lo último que le falta o la pasada de rescate)
                double minimo = asignadas.containsKey(p) || pasada == 1 ? MEDIA_HORA
                        : Math.min(HORAS_MINIMAS_POR_PASADA, p.restante);
                if (horas >= minimo) {
                    asignadas.merge(p, horas, Double::sum);
                    cuotas.put(p, cuotas.get(p) - horas);
                    saldoPorCurso.put(cursoId(p), saldo - horas);
                    libre -= horas;
                }
            }
        }
        return asignadas;
    }

    /** Reparte lo que falta en partes iguales entre los días de estudio que quedan, con tope diario. */
    private double cuotaDelDia(Pendiente p, LocalDate dia, Set<DayOfWeek> diasEstudio) {
        long diasQueQuedan = Math.max(1, dia.datesUntil(p.fechaLimite)
                .filter(d -> diasEstudio.contains(d.getDayOfWeek())).count());
        double reparto = redondearArriba(p.restante / diasQueQuedan);
        return redondearAbajo(Math.min(Math.min(reparto, p.maximoPorDia), p.restante));
    }

    /** Coloca bloques de hasta 90 min en las franjas libres del día. Devuelve las horas asignadas. */
    private double colocarBloques(Pendiente p, LocalDate dia, double horas, LocalTime desde, LocalTime horaDormir,
                                  List<HorarioClase> clases, List<BloqueEstudio> ocupados) {
        double asignadas = 0;
        while (horas - asignadas >= MEDIA_HORA) {
            Optional<IntervaloHorario> libre = elegirFranja(
                    disponibilidad.intervalosLibres(dia, desde, horaDormir, clases, ocupados), dia, clases);
            if (libre.isEmpty()) {
                break;
            }
            long minutos = Math.min(Math.round((horas - asignadas) * 60),
                    Math.min(libre.get().minutos(), MINUTOS_MAXIMOS_POR_BLOQUE));
            minutos = minutos / 30 * 30;
            if (minutos < 30) {
                break;
            }
            BloqueEstudio bloque = BloqueEstudio.builder()
                    .fecha(dia)
                    .horaInicio(libre.get().inicio())
                    .horaFin(libre.get().inicio().plusMinutes(minutos))
                    .descripcion(p.siguienteDescripcion(minutos / 60.0))
                    .build();
            p.evaluacion.agregarBloque(bloque);
            ocupados.add(bloque);
            asignadas += minutos / 60.0;
        }
        return asignadas;
    }

    /** Prefiere estudiar después de la última clase (si queda al menos 1 h libre); si no, la primera franja libre. */
    private Optional<IntervaloHorario> elegirFranja(List<IntervaloHorario> libres, LocalDate dia, List<HorarioClase> clases) {
        Optional<LocalTime> finClases = clases.stream()
                .filter(c -> c.getDiaSemana() == dia.getDayOfWeek())
                .map(HorarioClase::getHoraFin)
                .max(Comparator.naturalOrder());
        if (finClases.isPresent()) {
            Optional<IntervaloHorario> despues = libres.stream()
                    .filter(i -> !i.inicio().isBefore(finClases.get()) && i.minutos() >= 60)
                    .findFirst();
            if (despues.isPresent()) {
                return despues;
            }
        }
        return libres.stream().findFirst();
    }

    // ---------- Ayudantes ----------

    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Long cursoId(Pendiente p) {
        return p.evaluacion.getCurso().getId();
    }

    private double sumarHoras(List<BloqueEstudio> bloques) {
        return bloques.stream().mapToDouble(BloqueEstudio::getDuracionHoras).sum();
    }

    /** Redondea la hora actual a la siguiente media hora; null si ya pasó la medianoche. */
    private LocalTime siguienteMediaHora(LocalTime hora) {
        int minutos = hora.getHour() * 60 + hora.getMinute();
        int redondeado = (minutos + 29) / 30 * 30;
        return redondeado >= 24 * 60 ? null : LocalTime.of(redondeado / 60, redondeado % 60);
    }

    private double redondearArriba(double horas) {
        return Math.ceil(horas * 2) / 2.0;
    }

    private double redondearAbajo(double horas) {
        return Math.floor(horas * 2) / 2.0;
    }

    /** Estado de reparto de una evaluación mientras se arma el plan. */
    private static class Pendiente {
        final Evaluacion evaluacion;
        final LocalDate inicioVentana;
        final LocalDate fechaLimite;
        final double maximoPorDia;
        final List<PasoRepasoDto> pasos;
        double restante;
        double horasConsumidas;

        Pendiente(Evaluacion evaluacion, LocalDate inicioVentana, double maximoPorDia,
                  List<PasoRepasoDto> pasos, double restante, double horasConsumidas) {
            this.evaluacion = evaluacion;
            this.inicioVentana = inicioVentana;
            this.fechaLimite = evaluacion.getFecha();
            this.maximoPorDia = maximoPorDia;
            this.pasos = pasos;
            this.restante = restante;
            this.horasConsumidas = horasConsumidas;
        }

        /** Recorre los pasos del plan de repaso en orden: cada bloque estudia el paso que toca. */
        String siguienteDescripcion(double horas) {
            double acumulado = 0;
            String titulo = pasos.isEmpty() ? evaluacion.getNombre() : pasos.get(pasos.size() - 1).getTitulo();
            for (PasoRepasoDto paso : pasos) {
                acumulado += paso.getHoras();
                if (horasConsumidas < acumulado) {
                    titulo = paso.getTitulo();
                    break;
                }
            }
            horasConsumidas += horas;
            return titulo;
        }
    }
}

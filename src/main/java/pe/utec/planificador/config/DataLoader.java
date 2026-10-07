package pe.utec.planificador.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.*;
import pe.utec.planificador.repository.EstudianteRepository;
import pe.utec.planificador.service.CalculadoraPesoService;
import pe.utec.planificador.service.CalendarioAcademicoService;
import pe.utec.planificador.service.EstimadorHorasService;
import pe.utec.planificador.service.PlanificadorService;
import pe.utec.planificador.dto.ResultadoPlanDto;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** La primera vez que arranca la app crea el estudiante y, si se pide, cursos de ejemplo. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private static final LocalDate INICIO_CICLO = LocalDate.of(2026, 8, 10);

    private final EstudianteRepository estudianteRepository;
    private final CalendarioAcademicoService calendarioAcademico;
    private final CalculadoraPesoService calculadoraPeso;
    private final EstimadorHorasService estimadorHoras;
    private final PlanificadorService planificadorService;
    private final Clock reloj;

    /** true = carga 3 cursos de ejemplo (lo usan las pruebas); false = la app empieza sin cursos. */
    @Value("${planificador.datos-ejemplo:false}")
    private boolean cargarCursosDeEjemplo;

    @Override
    @Transactional
    public void run(String... args) {
        if (estudianteRepository.count() > 0) {
            return;
        }
        Estudiante estudiante = Estudiante.builder()
                .nombre("Estudiante")
                .email("estudiante@utec.edu.pe")
                .fechaInicioCiclo(INICIO_CICLO)
                .horasEstudioPorDia(3.0)
                .horaDormir(LocalTime.of(23, 0))
                .build();

        if (!cargarCursosDeEjemplo) {
            estudianteRepository.save(estudiante);
            log.info("Estudiante creado sin cursos: sube tus sílabos desde la app");
            return;
        }
        estudiante.agregarCurso(crearControlInteligente());
        estudiante.agregarCurso(crearEcuacionesDiferenciales());
        estudiante.agregarCurso(crearGestionProyectos());

        estudianteRepository.save(estudiante);
        ResultadoPlanDto plan = planificadorService.generarPlan(LocalDateTime.now(reloj));
        log.info("Datos de ejemplo cargados: {} cursos, {} bloques de estudio",
                estudiante.getCursos().size(), plan.getBloquesCreados());
    }

    // ---------- MT5307 – Control Inteligente (curso real) ----------

    private Curso crearControlInteligente() {
        Curso curso = nuevoCurso("MT5307", "Control Inteligente", 4, 4, "#7B5EA7");
        curso.agregarHorario(horario(DayOfWeek.MONDAY, 14, 16));
        curso.agregarHorario(horario(DayOfWeek.THURSDAY, 14, 16));

        Map<String, Tema> t = agregarTemas(curso, List.of(
                new DatoTema(1, "6.1", "Introducción al control inteligente"),
                new DatoTema(2, "6.2", "Sistemas difusos"),
                new DatoTema(3, "6.3", "Algoritmos evolutivos"),
                new DatoTema(4, "6.4", "Redes neuronales"),
                new DatoTema(5, "6.5", "Sistemas neuro-difusos (ANFIS)"),
                new DatoTema(6, "6.6", "Aprendizaje por refuerzo")));

        // Teoría: 3 EC = 60% de la nota (EC1 33%, EC2 33%, EC3 34%)
        evaluacion(curso, "EC1", TipoEvaluacion.EXAMEN, 6, DayOfWeek.MONDAY,
                calculadoraPeso.pesoReal(60, 33), t.get("6.1"), t.get("6.2"));
        evaluacion(curso, "EC2", TipoEvaluacion.EXAMEN, 11, DayOfWeek.MONDAY,
                calculadoraPeso.pesoReal(60, 33), t.get("6.3"), t.get("6.4"));
        evaluacion(curso, "EC3", TipoEvaluacion.EXAMEN, 15, DayOfWeek.MONDAY,
                calculadoraPeso.pesoReal(60, 34), t.get("6.5"), t.get("6.6"));
        evaluacion(curso, "Proyecto final", TipoEvaluacion.PROYECTO, 16, DayOfWeek.FRIDAY, 20.0);

        // Laboratorio: 20% de la nota (L1-L5 14% c/u, L6-L7 15% c/u)
        int[] semanasLab = {4, 6, 8, 10, 12, 14, 16};
        for (int i = 0; i < semanasLab.length; i++) {
            double pesoDentro = i < 5 ? 14 : 15;
            evaluacion(curso, "Laboratorio L" + (i + 1), TipoEvaluacion.LABORATORIO, semanasLab[i],
                    DayOfWeek.THURSDAY, calculadoraPeso.pesoReal(20, pesoDentro));
        }
        return curso;
    }

    // ---------- Cursos ficticios ----------

    private Curso crearEcuacionesDiferenciales() {
        Curso curso = nuevoCurso("MA3001", "Ecuaciones Diferenciales", 4, 4, "#D0694B");
        curso.agregarHorario(horario(DayOfWeek.TUESDAY, 10, 12));
        curso.agregarHorario(horario(DayOfWeek.THURSDAY, 10, 12));

        Map<String, Tema> t = agregarTemas(curso, List.of(
                new DatoTema(1, "1.1", "EDO de primer orden"),
                new DatoTema(1, "1.2", "Aplicaciones de EDO de primer orden"),
                new DatoTema(2, "2.1", "EDO lineales de orden superior"),
                new DatoTema(2, "2.2", "Transformada de Laplace"),
                new DatoTema(3, "3.1", "Sistemas de EDO lineales"),
                new DatoTema(3, "3.2", "Soluciones por series de potencias")));

        evaluacion(curso, "Práctica calificada 1", TipoEvaluacion.PRACTICA, 4, DayOfWeek.THURSDAY, 10.0, t.get("1.1"));
        evaluacion(curso, "Práctica calificada 2", TipoEvaluacion.PRACTICA, 7, DayOfWeek.THURSDAY, 10.0,
                t.get("1.2"), t.get("2.1"));
        evaluacion(curso, "Examen parcial", TipoEvaluacion.EXAMEN, 8, DayOfWeek.WEDNESDAY, 25.0,
                t.get("1.1"), t.get("1.2"), t.get("2.1"));
        evaluacion(curso, "Práctica calificada 3", TipoEvaluacion.PRACTICA, 11, DayOfWeek.THURSDAY, 10.0, t.get("2.2"));
        evaluacion(curso, "Práctica calificada 4", TipoEvaluacion.PRACTICA, 14, DayOfWeek.THURSDAY, 10.0, t.get("3.1"));
        evaluacion(curso, "Examen final", TipoEvaluacion.EXAMEN, 16, DayOfWeek.WEDNESDAY, 35.0,
                t.values().toArray(Tema[]::new));
        return curso;
    }

    private Curso crearGestionProyectos() {
        Curso curso = nuevoCurso("IN3020", "Gestión de Proyectos", 3, 2, "#5B8C5A");
        curso.agregarHorario(horario(DayOfWeek.WEDNESDAY, 18, 21));

        Map<String, Tema> t = agregarTemas(curso, List.of(
                new DatoTema(1, "1.1", "Ciclo de vida del proyecto"),
                new DatoTema(1, "1.2", "Acta de constitución"),
                new DatoTema(2, "2.1", "Alcance y EDT"),
                new DatoTema(2, "2.2", "Cronograma y ruta crítica"),
                new DatoTema(3, "3.1", "Costos y presupuesto"),
                new DatoTema(3, "3.2", "Gestión de riesgos")));

        evaluacion(curso, "Tarea 1: Acta de constitución", TipoEvaluacion.TAREA, 3, DayOfWeek.WEDNESDAY, 10.0, t.get("1.2"));
        evaluacion(curso, "Tarea 2: EDT", TipoEvaluacion.TAREA, 6, DayOfWeek.WEDNESDAY, 10.0, t.get("2.1"));
        evaluacion(curso, "Tarea 3: Cronograma", TipoEvaluacion.TAREA, 9, DayOfWeek.WEDNESDAY, 10.0, t.get("2.2"));
        evaluacion(curso, "Examen parcial", TipoEvaluacion.EXAMEN, 10, DayOfWeek.WEDNESDAY, 20.0,
                t.get("1.1"), t.get("1.2"), t.get("2.1"), t.get("2.2"));
        evaluacion(curso, "Tarea 4: Presupuesto", TipoEvaluacion.TAREA, 12, DayOfWeek.WEDNESDAY, 10.0, t.get("3.1"));
        evaluacion(curso, "Proyecto final", TipoEvaluacion.PROYECTO, 15, DayOfWeek.WEDNESDAY, 40.0);
        return curso;
    }

    // ---------- Ayudantes ----------

    private Curso nuevoCurso(String codigo, String nombre, int creditos, int dificultad, String color) {
        return Curso.builder()
                .codigo(codigo).nombre(nombre).creditos(creditos)
                .dificultad(dificultad).color(color)
                .build();
    }

    private HorarioClase horario(DayOfWeek dia, int horaInicio, int horaFin) {
        return HorarioClase.builder()
                .diaSemana(dia)
                .horaInicio(LocalTime.of(horaInicio, 0))
                .horaFin(LocalTime.of(horaFin, 0))
                .build();
    }

    /** Agrega temas al curso y los devuelve indexados por su número ("6.1" → Tema). */
    private Map<String, Tema> agregarTemas(Curso curso, List<DatoTema> datos) {
        datos.forEach(d -> curso.agregarTema(
                Tema.builder().unidad(d.unidad()).numero(d.numero()).titulo(d.titulo()).build()));
        return curso.getTemas().stream().collect(Collectors.toMap(
                Tema::getNumero, t -> t, (a, b) -> a, LinkedHashMap::new));
    }

    private record DatoTema(int unidad, String numero, String titulo) {
    }

    private void evaluacion(Curso curso, String nombre, TipoEvaluacion tipo, int semana,
                            DayOfWeek dia, double peso, Tema... temas) {
        Evaluacion evaluacion = Evaluacion.builder()
                .nombre(nombre)
                .tipo(tipo)
                .semana(semana)
                .fecha(calendarioAcademico.fechaDeSemana(INICIO_CICLO, semana, dia))
                .pesoEnNotaFinal(peso)
                .horasEstimadas(estimadorHoras.estimarHoras(tipo, curso.getDificultad(), peso))
                .temas(new ArrayList<>(List.of(temas)))
                .build();
        curso.agregarEvaluacion(evaluacion);
    }
}

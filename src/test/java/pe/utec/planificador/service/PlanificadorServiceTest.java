package pe.utec.planificador.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.RelojDePrueba;
import pe.utec.planificador.dto.ResultadoPlanDto;
import pe.utec.planificador.model.*;
import pe.utec.planificador.repository.BloqueEstudioRepository;
import pe.utec.planificador.repository.EvaluacionRepository;
import pe.utec.planificador.repository.HorarioClaseRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica las reglas de negocio del plan sobre los datos de ejemplo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:planificador;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@Import(RelojDePrueba.class)
@Transactional
class PlanificadorServiceTest {

    private static final LocalDate HOY = RelojDePrueba.AHORA.toLocalDate();

    @Autowired private PlanificadorService planificador;
    @Autowired private EstudianteService estudianteService;
    @Autowired private BloqueEstudioRepository bloqueRepository;
    @Autowired private EvaluacionRepository evaluacionRepository;
    @Autowired private HorarioClaseRepository horarioRepository;
    @Autowired private CalendarioAcademicoService calendario;

    private Estudiante estudiante;
    private List<BloqueEstudio> bloques;
    private ResultadoPlanDto resultado;

    @BeforeEach
    void generar() {
        resultado = planificador.generarPlan(RelojDePrueba.AHORA);
        assertThat(resultado.getBloquesCreados()).isPositive();
        estudiante = estudianteService.obtenerActual();
        bloques = bloqueRepository.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(estudiante.getId());
    }

    @Test
    void noSuperaLasHorasDeEstudioPorDia() {
        Map<LocalDate, Double> horasPorDia = bloques.stream().collect(Collectors.groupingBy(
                BloqueEstudio::getFecha, Collectors.summingDouble(BloqueEstudio::getDuracionHoras)));
        assertThat(horasPorDia.values()).allSatisfy(h -> assertThat(h).isLessThanOrEqualTo(estudiante.getHorasEstudioPorDia()));
    }

    @Test
    void noChocaConClasesNiConOtrosBloques() {
        List<HorarioClase> clases = horarioRepository.findByCursoEstudianteId(estudiante.getId());
        for (BloqueEstudio b : bloques) {
            int semana = calendario.semanaDeFecha(estudiante.getFechaInicioCiclo(), b.getFecha());
            clases.stream().filter(c -> c.getDiaSemana() == b.getFecha().getDayOfWeek() && c.aplicaEnSemana(semana)).forEach(c ->
                    assertThat(seCruzan(b.getHoraInicio(), b.getHoraFin(), c.getHoraInicio(), c.getHoraFin()))
                            .as("Bloque %s %s choca con clase", b.getFecha(), b.getHoraInicio()).isFalse());
            bloques.stream().filter(o -> o != b && o.getFecha().equals(b.getFecha())).forEach(o ->
                    assertThat(seCruzan(b.getHoraInicio(), b.getHoraFin(), o.getHoraInicio(), o.getHoraFin())).isFalse());
        }
    }

    @Test
    void elFinDeSemanaQuedaLibre() {
        assertThat(bloques).extracting(b -> b.getFecha().getDayOfWeek())
                .doesNotContain(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
    }

    @Test
    void ningunCursoPasaDeSusHorasPorSemana() {
        Map<String, Double> horasPorCursoYSemana = bloques.stream().collect(Collectors.groupingBy(
                b -> b.getEvaluacion().getCurso().getNombre() + " · "
                        + b.getFecha().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                Collectors.summingDouble(BloqueEstudio::getDuracionHoras)));
        assertThat(horasPorCursoYSemana).isNotEmpty();
        horasPorCursoYSemana.forEach((clave, horas) ->
                assertThat(horas).as(clave).isLessThanOrEqualTo(estudiante.getHorasMaximasCursoSemanaEfectivas()));
    }

    @Test
    void clasesMasEstudioNoPasanLaCargaMaximaDelDia() {
        List<HorarioClase> clases = horarioRepository.findByCursoEstudianteId(estudiante.getId());
        Map<LocalDate, Double> estudioPorDia = bloques.stream().collect(Collectors.groupingBy(
                BloqueEstudio::getFecha, Collectors.summingDouble(BloqueEstudio::getDuracionHoras)));
        estudioPorDia.forEach((dia, estudio) -> {
            int semana = calendario.semanaDeFecha(estudiante.getFechaInicioCiclo(), dia);
            double clase = clases.stream()
                    .filter(c -> c.getDiaSemana() == dia.getDayOfWeek() && c.aplicaEnSemana(semana))
                    .mapToDouble(c -> java.time.Duration.between(c.getHoraInicio(), c.getHoraFin()).toMinutes() / 60.0)
                    .sum();
            assertThat(estudio).as("estudio del %s", dia).isLessThanOrEqualTo(3.0);
            // Un día lleno de clases solo recibe el repaso corto de 1 h
            assertThat(clase + estudio).as("carga del %s", dia)
                    .isLessThanOrEqualTo(Math.max(estudiante.getCargaMaximaDiariaEfectiva(), clase + 1.0));
        });
    }

    @Test
    void respetaLaHoraDeDormirYNoEmpiezaAntesDeAhora() {
        LocalTime limite = estudiante.getHoraDormir().minusHours(1);
        assertThat(bloques).allSatisfy(b -> {
            assertThat(b.getHoraFin()).isBeforeOrEqualTo(limite);
            assertThat(b.getFecha()).isAfterOrEqualTo(HOY);
        });
    }

    @Test
    void estudiaAntesDeLaEvaluacionYNuncaTodoElUltimoDia() {
        for (Evaluacion e : evaluacionRepository.findByCursoEstudianteIdOrderByFechaAsc(estudiante.getId())) {
            List<BloqueEstudio> propios = e.getBloques();
            assertThat(propios).allSatisfy(b -> assertThat(b.getFecha()).isBefore(e.getFecha()));
            long dias = propios.stream().map(BloqueEstudio::getFecha).distinct().count();
            if (e.getFecha().isAfter(HOY.plusDays(2)) && e.getHorasEstimadas() > 2 && dias > 0) {
                assertThat(dias).as("%s %s se reparte en varios días", e.getCurso().getCodigo(), e.getNombre())
                        .isGreaterThan(1);
            }
            if (e.getFecha().isAfter(HOY) && dias == 0 && e.getHorasEstimadas() > 0) {
                // Sin tiempo en el plan (por los topes diarios y semanales): el estudiante recibe el aviso
                assertThat(resultado.getAdvertencias()).as("aviso de %s", e.getNombre())
                        .anyMatch(a -> a.contains(e.getCurso().getNombre() + " · " + e.getNombre()));
            }
        }
    }

    @Test
    void conservaLosBloquesCompletadosAlReplanificar() {
        BloqueEstudio primero = bloques.get(0);
        primero.setCompletado(true);
        planificador.generarPlan(RelojDePrueba.AHORA);
        assertThat(bloqueRepository.findById(primero.getId())).isPresent()
                .get().extracting(BloqueEstudio::isCompletado).isEqualTo(true);
    }

    private boolean seCruzan(LocalTime inicioA, LocalTime finA, LocalTime inicioB, LocalTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }
}

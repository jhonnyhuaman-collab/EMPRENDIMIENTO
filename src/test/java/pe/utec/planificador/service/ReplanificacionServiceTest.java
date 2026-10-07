package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.RelojDePrueba;
import pe.utec.planificador.dto.ReplanificacionDto;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.repository.BloqueEstudioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Simula que pasan dos días sin estudiar y verifica que el plan se reorganice solo. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:replanificacion;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@Import(RelojDePrueba.class)
@Transactional
class ReplanificacionServiceTest {

    private static final LocalDateTime DOS_DIAS_DESPUES = RelojDePrueba.AHORA.plusDays(2); // martes 06/10 08:00

    @Autowired private ReplanificacionService replanificacion;
    @Autowired private BloqueEstudioService bloqueEstudioService;
    @Autowired private EstudianteService estudianteService;
    @Autowired private BloqueEstudioRepository bloqueRepository;

    @Test
    void sinAtrasosNoHaceNada() {
        assertThat(replanificacion.replanificarSiHayAtrasos(RelojDePrueba.AHORA)).isEmpty();
    }

    @Test
    void reprogramaLosBloquesAtrasadosYRepartesSusHoras() {
        BloqueEstudio hecho = bloques().get(0);
        hecho.setCompletado(true); // el domingo solo hizo el primer bloque

        Optional<ReplanificacionDto> resultado = replanificacion.replanificarSiHayAtrasos(DOS_DIAS_DESPUES);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getBloquesAtrasados()).isPositive();
        assertThat(resultado.get().getMensaje()).contains("Reorganizamos tu plan");

        LocalDate martes = DOS_DIAS_DESPUES.toLocalDate();
        List<BloqueEstudio> despues = bloques();
        assertThat(despues).filteredOn(b -> b.getFecha().isBefore(martes) && !b.isCompletado())
                .isNotEmpty().allMatch(BloqueEstudio::isReprogramado);
        assertThat(despues).filteredOn(b -> !b.isReprogramado() && !b.isCompletado())
                .allMatch(b -> !b.getFecha().isBefore(martes));
        assertThat(bloqueRepository.findById(hecho.getId())).get()
                .matches(BloqueEstudio::isCompletado).matches(b -> !b.isReprogramado());

        // Una segunda visita el mismo día ya no vuelve a replanificar
        assertThat(replanificacion.replanificarSiHayAtrasos(DOS_DIAS_DESPUES)).isEmpty();
    }

    @Test
    void marcarComoHechoUnBloqueReprogramadoLoCuentaComoCompletado() {
        replanificacion.replanificarSiHayAtrasos(DOS_DIAS_DESPUES);
        BloqueEstudio reprogramado = bloques().stream().filter(BloqueEstudio::isReprogramado).findFirst().orElseThrow();

        bloqueEstudioService.alternarCompletado(reprogramado.getId(), DOS_DIAS_DESPUES);

        assertThat(bloqueRepository.findById(reprogramado.getId())).get()
                .matches(BloqueEstudio::isCompletado).matches(b -> !b.isReprogramado());
    }

    private List<BloqueEstudio> bloques() {
        return bloqueRepository.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(
                estudianteService.obtenerActual().getId());
    }
}

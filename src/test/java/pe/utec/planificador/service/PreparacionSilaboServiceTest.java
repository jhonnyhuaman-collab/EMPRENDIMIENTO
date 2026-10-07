package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import pe.utec.planificador.dto.formulario.FilaEvaluacion;
import pe.utec.planificador.dto.formulario.SilaboFormulario;
import pe.utec.planificador.dto.ia.*;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreparacionSilaboServiceTest {

    private final CursoService cursoService = mock(CursoService.class);
    private final PreparacionSilaboService preparacion =
            new PreparacionSilaboService(new CalculadoraPesoService(), new EstimadorHorasService(), cursoService,
                    new DiaEvaluacionService(new CalendarioAcademicoService()));

    @Test
    void aplicaLaReglaDePesoRealYCalculaHoras() {
        when(cursoService.listarCursos()).thenReturn(List.of());
        SilaboExtraidoDto extraido = new SilaboExtraidoDto("mt5307", "Control Inteligente", 4, 9,
                List.of(new TemaExtraidoDto(1, "6.1", "Introducción"), new TemaExtraidoDto(2, "6.2", "Sistemas difusos")),
                List.of(new EvaluacionExtraidaDto("EC1", TipoEvaluacion.EXAMEN, 6, 60, 33, List.of("6.1", "6.2")),
                        new EvaluacionExtraidaDto("Proyecto", TipoEvaluacion.PROYECTO, 16, 100, 20, List.of())),
                List.of(new HorarioExtraidoDto(DayOfWeek.THURSDAY, "14:00", "16:00"),
                        new HorarioExtraidoDto(DayOfWeek.FRIDAY, "mal", "16:00")),
                "Temas inferidos");

        SilaboFormulario formulario = preparacion.desdeIa(extraido, "silabo.pdf");

        assertThat(formulario.getCodigo()).isEqualTo("MT5307");
        assertThat(formulario.getDificultad()).isEqualTo(5); // se limita a 1-5
        assertThat(formulario.getColor()).isEqualTo(PaletaColores.COLORES.get(0));
        assertThat(formulario.getHorarios()).hasSize(1); // el horario ilegible se descarta
        assertThat(formulario.getHorarios().get(0).getHoraInicio()).isEqualTo(LocalTime.of(14, 0));

        FilaEvaluacion ec1 = formulario.getEvaluaciones().get(0);
        assertThat(ec1.getPesoEnNotaFinal()).isEqualTo(19.8);
        assertThat(ec1.getDetallePeso()).isEqualTo("60% × 33%");
        assertThat(ec1.getTemas()).isEqualTo("6.1, 6.2");
        assertThat(ec1.getDia()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(ec1.getHorasEstimadas()).isPositive();

        FilaEvaluacion proyecto = formulario.getEvaluaciones().get(1);
        assertThat(proyecto.getPesoEnNotaFinal()).isEqualTo(20.0);
        assertThat(proyecto.getDetallePeso()).isEmpty();
    }
}

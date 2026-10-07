package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pe.utec.planificador.dto.formulario.FilaCursoHorario;
import pe.utec.planificador.dto.formulario.HorarioFormulario;
import pe.utec.planificador.dto.ia.CursoMatriculadoDto;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SesionClaseDto;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.FrecuenciaClase;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreparacionHorarioServiceTest {

    private final CursoService cursoService = mock(CursoService.class);
    private final PreparacionHorarioService preparacion = new PreparacionHorarioService(cursoService);

    @Test
    void asignaUnColorDistintoPorCursoYRespetaLosExistentes() {
        Curso existente = Curso.builder().codigo("MT5307").nombre("Control Inteligente").color("#A44A6B").build();
        when(cursoService.listarCursos()).thenReturn(List.of(existente));

        HorarioMatriculaDto leido = new HorarioMatriculaDto(List.of(
                new CursoMatriculadoDto("mt5307", "Control Inteligente", 4, List.of(
                        new SesionClaseDto(DayOfWeek.THURSDAY, "14:00", "16:00", "Laboratorio · L-201", FrecuenciaClase.SEMANA_A),
                        new SesionClaseDto(DayOfWeek.MONDAY, "14:00", "16:00", "Teoría · A-505", FrecuenciaClase.SEMANAL))),
                new CursoMatriculadoDto("MA3001", "Ecuaciones Diferenciales", 4, List.of(
                        new SesionClaseDto(DayOfWeek.TUESDAY, "10:00", "12:00", "", FrecuenciaClase.SEMANAL),
                        new SesionClaseDto(DayOfWeek.FRIDAY, "12:00", "10:00", "", FrecuenciaClase.SEMANAL),       // horas invertidas: se descarta
                        new SesionClaseDto(DayOfWeek.FRIDAY, "ilegible", "10:00", "", FrecuenciaClase.SEMANAL))), // ilegible: se descarta
                new CursoMatriculadoDto("", "Cálculo de Varias Variables", 5, List.of()),
                new CursoMatriculadoDto("MA3001", "Repetido", 4, List.of())),
                "Una celda borrosa");

        HorarioFormulario formulario = preparacion.desdeIa(leido, "horario.png");

        assertThat(formulario.getCursos()).extracting(FilaCursoHorario::getCodigo)
                .containsExactly("MT5307", "MA3001", "CVV");
        FilaCursoHorario control = formulario.getCursos().get(0);
        assertThat(control.isExistente()).isTrue();
        assertThat(control.getColor()).isEqualTo("#A44A6B");
        Set<String> colores = new HashSet<>();
        formulario.getCursos().forEach(c -> colores.add(c.getColor()));
        assertThat(colores).hasSize(3);

        assertThat(formulario.getSesiones()).hasSize(3);
        assertThat(formulario.getSesiones().get(0).getDia()).isEqualTo(DayOfWeek.MONDAY); // ordenadas por día
        assertThat(formulario.getSesiones().get(0).getHoraInicio()).isEqualTo(LocalTime.of(14, 0));
        assertThat(formulario.getObservaciones()).isEqualTo("Una celda borrosa");
        assertThat(formulario.getSesiones()).filteredOn(f -> f.getDia() == DayOfWeek.THURSDAY)
                .singleElement().satisfies(f -> assertThat(f.getFrecuencia()).isEqualTo(FrecuenciaClase.SEMANA_A));
    }

    @Test
    void generaCodigosUnicosDesdeElNombre() {
        Set<String> usados = new HashSet<>(Set.of("GDP"));
        assertThat(preparacion.codigoDesdeNombre("Gestión de Proyectos", usados)).isEqualTo("GP");
        usados.add("GP");
        assertThat(preparacion.codigoDesdeNombre("Gestión de Proyectos", usados)).isEqualTo("GP2");
    }

    @Test
    void aceptaImagenesYPdfPeroNoHeic() {
        LectorArchivoHorarioService lector = new LectorArchivoHorarioService();
        assertThat(lector.leer(new MockMultipartFile("archivo", "horario.PNG", "image/png", new byte[]{1})).tipoMime())
                .isEqualTo("image/png");
        assertThat(lector.leer(new MockMultipartFile("archivo", "matricula.pdf", "application/pdf", new byte[]{1})).esPdf())
                .isTrue();
        assertThatThrownBy(() -> lector.leer(new MockMultipartFile("archivo", "foto.heic", "image/heic", new byte[]{1})))
                .isInstanceOf(SilaboException.class).hasMessageContaining("HEIC");
        assertThatThrownBy(() -> lector.leer(new MockMultipartFile("archivo", "horario.docx", "application/msword", new byte[]{1})))
                .isInstanceOf(SilaboException.class).hasMessageContaining("Formato no compatible");
    }
}

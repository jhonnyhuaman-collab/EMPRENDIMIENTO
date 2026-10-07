package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioAcademicoServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 8, 17); // lunes
    private final CalendarioAcademicoService servicio = new CalendarioAcademicoService();

    @Test
    void semanaUnoEsLaFechaDeInicio() {
        assertThat(servicio.fechaDeSemana(INICIO, 1, DayOfWeek.MONDAY)).isEqualTo(INICIO);
    }

    @Test
    void convierteSemanaYDiaEnFecha() {
        assertThat(servicio.fechaDeSemana(INICIO, 6, DayOfWeek.MONDAY)).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(servicio.fechaDeSemana(INICIO, 8, DayOfWeek.WEDNESDAY)).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void calculaLaSemanaDeUnaFecha() {
        assertThat(servicio.semanaDeFecha(INICIO, LocalDate.of(2026, 10, 4))).isEqualTo(7);
        assertThat(servicio.semanaDeFecha(INICIO, LocalDate.of(2026, 10, 5))).isEqualTo(8);
        assertThat(servicio.semanaDeFecha(INICIO, LocalDate.of(2026, 8, 1))).isZero();
    }
}

package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.FrecuenciaClase;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiaEvaluacionServiceTest {

    private final DiaEvaluacionService servicio = new DiaEvaluacionService(new CalendarioAcademicoService());

    private final List<HorarioClase> sesiones = List.of(
            clase(DayOfWeek.WEDNESDAY, "Laboratorio · L-201", FrecuenciaClase.SEMANA_B),
            clase(DayOfWeek.MONDAY, "Teoría · A-505", FrecuenciaClase.SEMANAL),
            clase(DayOfWeek.THURSDAY, "Laboratorio · L-105", FrecuenciaClase.SEMANA_A));

    @Test
    void semanaAEsImparYSemanaBEsPar() {
        assertThat(FrecuenciaClase.SEMANA_A.aplicaEnSemana(1)).isTrue();
        assertThat(FrecuenciaClase.SEMANA_A.aplicaEnSemana(2)).isFalse();
        assertThat(FrecuenciaClase.SEMANA_B.aplicaEnSemana(2)).isTrue();
        assertThat(FrecuenciaClase.SEMANA_B.aplicaEnSemana(7)).isFalse();
        assertThat(FrecuenciaClase.SEMANAL.aplicaEnSemana(7)).isTrue();
    }

    @Test
    void elLaboratorioCaeElDiaDeLaboratorioDeEsaSemana() {
        assertThat(servicio.elegirDia(TipoEvaluacion.LABORATORIO, 4, sesiones)).isEqualTo(DayOfWeek.WEDNESDAY); // B
        assertThat(servicio.elegirDia(TipoEvaluacion.LABORATORIO, 5, sesiones)).isEqualTo(DayOfWeek.THURSDAY);  // A
    }

    @Test
    void examenesYPracticasVanElDiaDeTeoria() {
        assertThat(servicio.elegirDia(TipoEvaluacion.EXAMEN, 8, sesiones)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(servicio.elegirDia(TipoEvaluacion.PRACTICA, 3, sesiones)).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void siElLaboratorioNoCaeEsaSemanaIgualVaElDiaDeLaboratorio() {
        // Control Inteligente: teoría lunes, laboratorio jueves en Semana A; el sílabo pone labs en semanas pares
        List<HorarioClase> control = List.of(
                clase(DayOfWeek.MONDAY, "Teoría · M803", FrecuenciaClase.SEMANAL),
                clase(DayOfWeek.THURSDAY, "Laboratorio · L413", FrecuenciaClase.SEMANA_A));
        assertThat(servicio.elegirDia(TipoEvaluacion.LABORATORIO, 4, control)).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(servicio.elegirDia(TipoEvaluacion.EXAMEN, 11, control)).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void reubicarPoneDiaYHoraDeClase() {
        Curso curso = Curso.builder().build();
        curso.agregarHorario(clase(DayOfWeek.MONDAY, "Teoría", FrecuenciaClase.SEMANAL));
        curso.agregarHorario(HorarioClase.builder().diaSemana(DayOfWeek.THURSDAY).horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(22, 0)).detalle("Laboratorio · L413").frecuencia(FrecuenciaClase.SEMANA_A).build());
        Evaluacion lab = Evaluacion.builder().nombre("L4").tipo(TipoEvaluacion.LABORATORIO).semana(10)
                .fecha(LocalDate.of(2026, 10, 12)).build();
        curso.agregarEvaluacion(lab);

        servicio.reubicar(curso, LocalDate.of(2026, 8, 10));

        assertThat(lab.getFecha()).isEqualTo(LocalDate.of(2026, 10, 15)); // jueves de la semana 10
        assertThat(lab.getHoraEntrega()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void sinHorarioUsaLunes() {
        assertThat(servicio.elegirDia(TipoEvaluacion.TAREA, 3, List.of())).isEqualTo(DayOfWeek.MONDAY);
    }

    private HorarioClase clase(DayOfWeek dia, String detalle, FrecuenciaClase frecuencia) {
        return HorarioClase.builder().diaSemana(dia).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(12, 0))
                .detalle(detalle).frecuencia(frecuencia).build();
    }
}

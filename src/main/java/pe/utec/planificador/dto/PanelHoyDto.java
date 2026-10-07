package pe.utec.planificador.dto;

import lombok.Builder;
import lombok.Value;
import pe.utec.planificador.model.BloqueEstudio;

import java.time.LocalDate;
import java.util.List;

/** Datos que muestra la pantalla "Hoy". */
@Value
@Builder
public class PanelHoyDto {

    LocalDate hoy;
    String saludo;
    String nombreEstudiante;
    int semanaCiclo;
    int totalSemanas;
    List<BloqueEstudio> bloquesHoy;
    double horasPlanificadasHoy;
    double horasCompletadasHoy;
    List<EntregaProximaDto> entregasSemana;
    boolean semanaCritica;
    List<EntregaProximaDto> evaluacionesCriticas;
    List<CargaDiaDto> cargaProximosDias;
    boolean tienePlan;
    boolean tieneCursos;
    /** Próxima evaluación aunque falten más de 7 días; null si no hay ninguna. */
    EntregaProximaDto proximaEvaluacion;
    /** Próximo bloque de estudio después de hoy; null si no hay. */
    BloqueEstudio proximoBloque;
    /** Códigos de cursos que tienen horario pero aún no sílabo. */
    List<String> cursosSinSilabo;

    public int getPorcentajeCiclo() {
        return (int) Math.round(100.0 * Math.min(semanaCiclo, totalSemanas) / totalSemanas);
    }

    public int getPorcentajeHoy() {
        return horasPlanificadasHoy == 0 ? 0 : (int) Math.round(100 * horasCompletadasHoy / horasPlanificadasHoy);
    }

    public double getPesoEnJuegoSemana() {
        return entregasSemana.stream().mapToDouble(e -> e.getEvaluacion().getPesoEnNotaFinal()).sum();
    }

    public EntregaProximaDto getProximaEntrega() {
        return entregasSemana.isEmpty() ? null : entregasSemana.get(0);
    }
}

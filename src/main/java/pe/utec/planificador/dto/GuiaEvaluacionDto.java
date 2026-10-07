package pe.utec.planificador.dto;

import lombok.Builder;
import lombok.Value;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.NivelPrioridad;

import java.time.LocalDate;
import java.util.List;

/** Datos de la pantalla "Detalle de evaluación". */
@Value
@Builder
public class GuiaEvaluacionDto {

    Evaluacion evaluacion;
    long diasRestantes;
    LocalDate inicioRecomendado;
    NivelPrioridad prioridad;
    List<PasoRepasoDto> planRepaso;
    double horasPlanificadas;
    double horasCompletadas;

    public int getPorcentajeAvance() {
        return horasPlanificadas == 0 ? 0 : (int) Math.round(100 * horasCompletadas / horasPlanificadas);
    }
}

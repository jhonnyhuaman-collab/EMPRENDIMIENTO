package pe.utec.planificador.dto;

import lombok.Value;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.model.NivelPrioridad;

/** Evaluación próxima con los días que faltan y su prioridad. */
@Value
public class EntregaProximaDto {

    Evaluacion evaluacion;
    long diasRestantes;
    NivelPrioridad prioridad;

    /** Faltan 3 días o menos y no se ha entregado. */
    public boolean isUrgente() {
        return diasRestantes >= 0 && diasRestantes <= 3 && !evaluacion.isCompletada();
    }

    public String getTextoDias() {
        if (diasRestantes == 0) return "hoy";
        if (diasRestantes == 1) return "mañana";
        return "en " + diasRestantes + " días";
    }
}

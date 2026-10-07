package pe.utec.planificador.dto;

import lombok.Value;

import java.time.LocalDate;

/** Horas de estudio planificadas en un día (para el gráfico de carga semanal). */
@Value
public class CargaDiaDto {

    LocalDate fecha;
    double horas;
    double horasCompletadas;
    double horasMaximas;
    boolean hoy;

    /** Altura de la barra en % respecto al máximo diario. */
    public int getPorcentaje() {
        return horasMaximas == 0 ? 0 : (int) Math.min(100, Math.round(100 * horas / horasMaximas));
    }
}

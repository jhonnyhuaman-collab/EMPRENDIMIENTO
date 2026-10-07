package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaEvaluacion {

    @NotBlank(message = "Nombre requerido")
    private String nombre;

    @NotNull
    private TipoEvaluacion tipo;

    @NotNull @Min(value = 1, message = "Semana entre 1 y 20") @Max(value = 20, message = "Semana entre 1 y 20")
    private Integer semana;

    /** Día de la semana en que se rinde (la fecha se calcula con la semana). */
    @NotNull
    private DayOfWeek dia;

    @NotNull @DecimalMin(value = "0", message = "Peso inválido") @DecimalMax(value = "100", message = "Peso inválido")
    private Double pesoEnNotaFinal;

    @NotNull @DecimalMin(value = "0.5", message = "Mínimo 0.5 h") @DecimalMax(value = "200")
    private Double horasEstimadas;

    /** Números de tema separados por coma, ej. "6.1, 6.2". */
    private String temas;

    /** Cómo se calculó el peso, ej. "60% × 33%" (solo informativo). */
    private String detallePeso;
}

package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.LocalDate;
import java.time.LocalTime;

/** Un pendiente de la agenda: tarea, quiz o evaluación que no estaba en el sílabo. */
@Data
public class PendienteFormulario {

    @NotNull(message = "Elige el curso")
    private Long cursoId;

    @NotBlank(message = "Escribe qué tienes que entregar o rendir")
    @Size(max = 120)
    private String nombre;

    @NotNull
    private TipoEvaluacion tipo = TipoEvaluacion.TAREA;

    @NotNull(message = "Elige la fecha")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;

    /** Hora límite opcional, ej. 23:59. */
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaEntrega;

    /** Peso en la nota final, si lo sabes (opcional). */
    @DecimalMin(value = "0", message = "Peso inválido")
    @DecimalMax(value = "100", message = "Peso inválido")
    private Double pesoEnNotaFinal;

    /** Horas que crees que te tomará; vacío = la app lo estima. */
    @DecimalMin(value = "0.5", message = "Mínimo 0.5 h")
    @DecimalMax(value = "100")
    private Double horasEstimadas;

    @Size(max = 500)
    private String notas;
}

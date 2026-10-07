package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import pe.utec.planificador.model.FrecuenciaClase;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaSesion {

    /** Código del curso al que pertenece la sesión. */
    @NotBlank(message = "Curso requerido")
    private String codigoCurso;

    @NotNull
    private DayOfWeek dia;

    @NotNull(message = "Hora requerida")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @NotNull(message = "Hora requerida")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaFin;

    @Size(max = 80)
    private String detalle;

    @NotNull
    private FrecuenciaClase frecuencia = FrecuenciaClase.SEMANAL;
}

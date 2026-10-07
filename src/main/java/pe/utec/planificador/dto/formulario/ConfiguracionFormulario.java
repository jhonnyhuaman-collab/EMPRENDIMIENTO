package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Datos del estudiante que definen el calendario y el plan. */
@Data
public class ConfiguracionFormulario {

    @Size(max = 100, message = "Usa como maximo 100 caracteres")
    private String nombre;

    /** Lunes de la semana 1 del ciclo. */
    @NotNull(message = "Elige la fecha de inicio del ciclo")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicioCiclo;

    @NotNull
    @DecimalMin(value = "0.5", message = "Mínimo 0.5 h")
    @DecimalMax(value = "12", message = "Máximo 12 h")
    private Double horasEstudioPorDia;

    @NotNull
    @DecimalMin(value = "3", message = "Mínimo 3 h")
    @DecimalMax(value = "16", message = "Máximo 16 h")
    private Double cargaMaximaDiaria;

    @NotNull
    @DecimalMin(value = "1", message = "Mínimo 1 h")
    @DecimalMax(value = "20", message = "Máximo 20 h")
    private Double horasMaximasCursoSemana;

    /** Días en que el plan puede poner estudio. */
    @NotEmpty(message = "Elige al menos un día de estudio")
    private List<DayOfWeek> diasEstudio = new ArrayList<>();

    @NotNull(message = "Elige tu hora de dormir")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaDormir;
}

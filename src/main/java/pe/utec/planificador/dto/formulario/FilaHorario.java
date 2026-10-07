package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaHorario {

    @NotNull
    private DayOfWeek dia;

    @NotNull(message = "Hora requerida")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @NotNull(message = "Hora requerida")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaFin;
}

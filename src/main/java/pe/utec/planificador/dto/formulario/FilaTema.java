package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaTema {

    @NotNull @Min(0)
    private Integer unidad;

    @NotBlank(message = "Número de tema requerido")
    private String numero;

    @NotBlank(message = "Título de tema requerido")
    private String titulo;
}

package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilaCursoHorario {

    @NotBlank(message = "Código requerido")
    @Size(max = 20)
    private String codigo;

    @NotBlank(message = "Nombre requerido")
    @Size(max = 120)
    private String nombre;

    @NotNull @Min(0) @Max(10)
    private Integer creditos;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color inválido")
    private String color;

    /** true si el curso ya existe (se actualizará su horario y se conservan sus evaluaciones). */
    private boolean existente;
}

package pe.utec.planificador.dto.formulario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Tabla editable de revisión: lo extraído del sílabo antes de guardarlo. */
@Data
public class SilaboFormulario {

    @NotBlank(message = "Ingresa el código del curso")
    @Size(max = 20)
    private String codigo;

    @NotBlank(message = "Ingresa el nombre del curso")
    @Size(max = 120)
    private String nombre;

    @NotNull @Min(0) @Max(10)
    private Integer creditos = 0;

    @NotNull @Min(1) @Max(5)
    private Integer dificultad = 3;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color inválido")
    private String color;

    private String nombreArchivo;

    /** Código del curso ya registrado (p. ej. desde el horario) que este sílabo completa; vacío si es nuevo. */
    private String codigoExistente;
    private String observaciones;

    @Valid
    private List<FilaTema> temas = new ArrayList<>();

    @Valid
    @NotEmpty(message = "Agrega al menos una evaluación")
    private List<FilaEvaluacion> evaluaciones = new ArrayList<>();

    @Valid
    private List<FilaHorario> horarios = new ArrayList<>();

    public double getPesoTotal() {
        return evaluaciones.stream()
                .mapToDouble(e -> e.getPesoEnNotaFinal() == null ? 0 : e.getPesoEnNotaFinal())
                .sum();
    }
}

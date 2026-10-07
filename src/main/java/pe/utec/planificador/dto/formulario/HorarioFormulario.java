package pe.utec.planificador.dto.formulario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Tabla editable de revisión del horario: cursos y sus sesiones de clase. */
@Data
public class HorarioFormulario {

    private String nombreArchivo;
    private String observaciones;

    @Valid
    @NotEmpty(message = "Agrega al menos un curso")
    private List<FilaCursoHorario> cursos = new ArrayList<>();

    @Valid
    private List<FilaSesion> sesiones = new ArrayList<>();
}

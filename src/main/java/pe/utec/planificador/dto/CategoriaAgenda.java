package pe.utec.planificador.dto;

import lombok.Getter;
import pe.utec.planificador.model.TipoEvaluacion;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Grupos de la agenda para no mezclar exámenes, laboratorios y tareas en una sola lista. */
@Getter
public enum CategoriaAgenda {
    EXAMENES("Exámenes y prácticas", "bi-journal-check", EnumSet.of(TipoEvaluacion.EXAMEN, TipoEvaluacion.PRACTICA)),
    LABORATORIOS("Laboratorios", "bi-cpu", EnumSet.of(TipoEvaluacion.LABORATORIO)),
    TAREAS("Tareas y proyectos", "bi-file-earmark-text", EnumSet.of(TipoEvaluacion.TAREA, TipoEvaluacion.PROYECTO));

    private final String etiqueta;
    private final String icono;
    private final Set<TipoEvaluacion> tipos;

    CategoriaAgenda(String etiqueta, String icono, Set<TipoEvaluacion> tipos) {
        this.etiqueta = etiqueta;
        this.icono = icono;
        this.tipos = tipos;
    }

    /** Valor para la URL, ej. ?ver=laboratorios. */
    public String getParametro() {
        return name().toLowerCase();
    }

    public boolean incluye(TipoEvaluacion tipo) {
        return tipos.contains(tipo);
    }

    public static Optional<CategoriaAgenda> desdeParametro(String parametro) {
        return Arrays.stream(values()).filter(c -> c.getParametro().equalsIgnoreCase(parametro)).findFirst();
    }
}

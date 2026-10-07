package pe.utec.planificador.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoEvaluacion {
    PRACTICA("Práctica", "bi-pencil-square"),
    EXAMEN("Examen", "bi-journal-check"),
    LABORATORIO("Laboratorio", "bi-cpu"),
    PROYECTO("Proyecto", "bi-kanban"),
    TAREA("Tarea", "bi-file-earmark-text");

    private final String etiqueta;
    private final String icono;
}

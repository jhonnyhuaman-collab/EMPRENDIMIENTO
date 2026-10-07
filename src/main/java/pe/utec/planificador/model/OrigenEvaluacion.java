package pe.utec.planificador.model;

/** De dónde salió una evaluación. */
public enum OrigenEvaluacion {
    /** Extraída del sílabo (o registrada en la tabla del sílabo). */
    SILABO,
    /** Agregada a mano en la agenda: tareas, quizzes o evaluaciones anunciadas en clase. */
    AGENDA
}

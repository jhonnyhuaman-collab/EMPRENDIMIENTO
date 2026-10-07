package pe.utec.planificador.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Cada cuánto se dicta una clase. En UTEC, "Semana A" = semanas impares y "Semana B" = semanas pares. */
@Getter
@RequiredArgsConstructor
public enum FrecuenciaClase {
    SEMANAL("Todas las semanas", ""),
    SEMANA_A("Semana A (impares)", "Sem A"),
    SEMANA_B("Semana B (pares)", "Sem B");

    private final String etiqueta;
    private final String etiquetaCorta;

    /** ¿Hay clase en esa semana del ciclo? (semana 1 = A, semana 2 = B, ...) */
    public boolean aplicaEnSemana(int semana) {
        return switch (this) {
            case SEMANAL -> true;
            case SEMANA_A -> semana % 2 == 1;
            case SEMANA_B -> semana % 2 == 0;
        };
    }

    public boolean esQuincenal() {
        return this != SEMANAL;
    }
}

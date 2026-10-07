package pe.utec.planificador.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NivelPrioridad {
    ALTA("Prioridad alta", "prioridad-alta"),
    MEDIA("Prioridad media", "prioridad-media"),
    BAJA("Prioridad baja", "prioridad-baja");

    private final String etiqueta;
    private final String claseCss;
}

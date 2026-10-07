package pe.utec.planificador.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Colores premium para los cursos (sin azules), asignados en orden sin repetir mientras alcancen. */
public final class PaletaColores {

    public static final List<String> COLORES = List.of(
            "#7B5EA7", // amatista
            "#D0694B", // terracota
            "#5B8C5A", // salvia
            "#A44A6B", // frambuesa
            "#2F7F86", // petróleo
            "#9A7A3E", // bronce
            "#5E4B8B", // ciruela
            "#C8553D", // coral
            "#6B8E23", // oliva
            "#B5838D"); // rosa antiguo

    private PaletaColores() {
    }

    /** Primer color que no esté en uso; si todos están usados, se repite en orden. */
    public static String siguiente(Set<String> usados) {
        return COLORES.stream()
                .filter(c -> usados.stream().noneMatch(u -> u.equalsIgnoreCase(c)))
                .findFirst()
                .orElse(COLORES.get(usados.size() % COLORES.size()));
    }

    static String normalizar(String color) {
        return color == null ? "" : color.toUpperCase(Locale.ROOT);
    }
}

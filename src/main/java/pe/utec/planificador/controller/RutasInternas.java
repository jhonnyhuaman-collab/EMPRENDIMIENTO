package pe.utec.planificador.controller;

/** Valida rutas de retorno para no redirigir a sitios externos. */
final class RutasInternas {

    private RutasInternas() {
    }

    static String segura(String ruta) {
        return ruta != null && ruta.startsWith("/") && !ruta.startsWith("//") ? ruta : "/";
    }
}

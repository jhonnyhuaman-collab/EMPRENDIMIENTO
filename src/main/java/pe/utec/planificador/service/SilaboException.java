package pe.utec.planificador.service;

import lombok.Getter;

/** Error al procesar un sílabo u horario; el mensaje se muestra tal cual al estudiante. */
@Getter
public class SilaboException extends RuntimeException {

    /** true si es pasajero (saturación, límite de uso, sin conexión): conviene probar con otro proveedor de IA. */
    private final boolean temporal;

    public SilaboException(String mensaje) {
        this(mensaje, null, false);
    }

    public SilaboException(String mensaje, Throwable causa) {
        this(mensaje, causa, false);
    }

    private SilaboException(String mensaje, Throwable causa, boolean temporal) {
        super(mensaje, causa);
        this.temporal = temporal;
    }

    public static SilaboException temporal(String mensaje, Throwable causa) {
        return new SilaboException(mensaje, causa, true);
    }

    public static SilaboException temporal(String mensaje) {
        return new SilaboException(mensaje, null, true);
    }
}

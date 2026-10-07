package pe.utec.planificador.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Se lanza cuando no existe el registro pedido; responde 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntidadNoEncontradaException extends RuntimeException {

    public EntidadNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}

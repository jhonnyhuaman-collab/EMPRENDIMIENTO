package pe.utec.planificador.dto;

import lombok.Value;

/** Resumen de una importación de horario. */
@Value
public class ResultadoHorarioDto {

    int cursosNuevos;
    int cursosActualizados;
    int sesiones;

    public String getMensaje() {
        StringBuilder mensaje = new StringBuilder("Horario importado: ");
        if (cursosNuevos > 0) {
            mensaje.append(cursosNuevos).append(cursosNuevos == 1 ? " curso nuevo" : " cursos nuevos");
        }
        if (cursosActualizados > 0) {
            mensaje.append(cursosNuevos > 0 ? ", " : "")
                    .append(cursosActualizados).append(cursosActualizados == 1 ? " actualizado" : " actualizados");
        }
        return mensaje.append(" y ").append(sesiones).append(sesiones == 1 ? " clase por semana." : " clases por semana.")
                .toString();
    }
}

package pe.utec.planificador.dto;

import lombok.Value;

import java.util.List;

/** Una sección de la agenda (ej. "Laboratorios") con sus entregas agrupadas por día. */
@Value
public class SeccionAgendaDto {

    CategoriaAgenda categoria;
    List<DiaAgendaDto> dias;

    public int getTotal() {
        return dias.stream().mapToInt(d -> d.getEntregas().size()).sum();
    }
}

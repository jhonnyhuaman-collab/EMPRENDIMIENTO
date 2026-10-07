package pe.utec.planificador.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.IntervaloHorario;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.HorarioClase;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Calcula las franjas libres de un día: entre el inicio de jornada y la hora de dormir, sin clases ni otros bloques. */
@Service
public class DisponibilidadService {

    static final int MINUTOS_DESCANSO = 15;
    static final int MINUTOS_MINIMOS = 30;
    /** Se deja de estudiar 1 hora antes de dormir. */
    private static final int MINUTOS_ANTES_DE_DORMIR = 60;

    private final LocalTime inicioJornada;

    public DisponibilidadService(@Value("${planificador.hora-inicio-estudio:08:00}") LocalTime inicioJornada) {
        this.inicioJornada = inicioJornada;
    }

    /**
     * @param desde hora mínima para empezar (para hoy, la hora actual); null = inicio de jornada
     */
    public List<IntervaloHorario> intervalosLibres(LocalDate dia, LocalTime desde, LocalTime horaDormir,
                                                   List<HorarioClase> clases, List<BloqueEstudio> bloquesDelDia) {
        LocalTime inicio = desde != null && desde.isAfter(inicioJornada) ? desde : inicioJornada;
        LocalTime fin = finDeJornada(horaDormir);

        List<IntervaloHorario> ocupados = new ArrayList<>();
        clases.stream()
                .filter(c -> c.getDiaSemana() == dia.getDayOfWeek())
                .forEach(c -> ocupados.add(new IntervaloHorario(c.getHoraInicio(), conDescanso(c.getHoraFin()))));
        bloquesDelDia.forEach(b -> ocupados.add(new IntervaloHorario(b.getHoraInicio(), conDescanso(b.getHoraFin()))));
        ocupados.sort(Comparator.comparing(IntervaloHorario::inicio));

        List<IntervaloHorario> libres = new ArrayList<>();
        LocalTime cursor = inicio;
        for (IntervaloHorario ocupado : ocupados) {
            if (ocupado.inicio().isAfter(cursor)) {
                agregarSiAlcanza(libres, cursor, minimo(ocupado.inicio(), fin));
            }
            if (ocupado.fin().isAfter(cursor)) {
                cursor = ocupado.fin();
            }
        }
        agregarSiAlcanza(libres, cursor, fin);
        return libres;
    }

    /** Si se duerme después de medianoche, se estudia como máximo hasta las 23:00. */
    private LocalTime finDeJornada(LocalTime horaDormir) {
        if (horaDormir == null || horaDormir.isBefore(LocalTime.NOON)) {
            return LocalTime.of(23, 0);
        }
        return horaDormir.minusMinutes(MINUTOS_ANTES_DE_DORMIR);
    }

    private LocalTime conDescanso(LocalTime hora) {
        LocalTime conDescanso = hora.plusMinutes(MINUTOS_DESCANSO);
        return conDescanso.isBefore(hora) ? LocalTime.MAX : conDescanso; // evita pasar de medianoche
    }

    private LocalTime minimo(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    private void agregarSiAlcanza(List<IntervaloHorario> libres, LocalTime inicio, LocalTime fin) {
        IntervaloHorario intervalo = new IntervaloHorario(inicio, fin);
        if (inicio.isBefore(fin) && intervalo.minutos() >= MINUTOS_MINIMOS) {
            libres.add(intervalo);
        }
    }
}

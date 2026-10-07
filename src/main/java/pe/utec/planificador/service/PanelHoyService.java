package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.dto.CargaDiaDto;
import pe.utec.planificador.dto.EntregaProximaDto;
import pe.utec.planificador.dto.PanelHoyDto;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.repository.BloqueEstudioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Arma el panel "Hoy": bloques del día, entregas cercanas, carga semanal y alerta de semana crítica. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PanelHoyService {

    static final int DIAS_ENTREGAS_SEMANA = 7;
    static final int DIAS_VENTANA_CRITICA = 14;
    static final int MINIMO_EVALUACIONES_CRITICAS = 3;
    /** Hasta dónde buscar la próxima evaluación (todo el ciclo). */
    static final int DIAS_HORIZONTE = 180;

    private final EstudianteService estudianteService;
    private final EvaluacionService evaluacionService;
    private final BloqueEstudioRepository bloqueRepository;
    private final CalendarioAcademicoService calendarioAcademico;

    public PanelHoyDto construirPanel(LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        Estudiante estudiante = estudianteService.obtenerActual();
        List<BloqueEstudio> semana = bloqueRepository.findByEvaluacionCursoEstudianteIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                estudiante.getId(), hoy, hoy.plusDays(DIAS_ENTREGAS_SEMANA - 1L));
        List<BloqueEstudio> bloquesHoy = semana.stream().filter(b -> b.getFecha().equals(hoy)).toList();
        List<EntregaProximaDto> criticas = evaluacionService.proximas(hoy, DIAS_VENTANA_CRITICA);

        return PanelHoyDto.builder()
                .hoy(hoy)
                .saludo(saludo(ahora.getHour()))
                .nombreEstudiante(primerNombre(estudiante.getNombre()))
                .semanaCiclo(calendarioAcademico.semanaDeFecha(estudiante.getFechaInicioCiclo(), hoy))
                .totalSemanas(CalendarioAcademicoService.TOTAL_SEMANAS)
                .bloquesHoy(bloquesHoy)
                .horasPlanificadasHoy(sumarHoras(bloquesHoy, false))
                .horasCompletadasHoy(sumarHoras(bloquesHoy, true))
                .entregasSemana(evaluacionService.proximas(hoy, DIAS_ENTREGAS_SEMANA))
                .semanaCritica(criticas.size() >= MINIMO_EVALUACIONES_CRITICAS)
                .evaluacionesCriticas(criticas)
                .cargaProximosDias(cargaPorDia(semana, hoy, estudiante.getHorasEstudioPorDia()))
                .tienePlan(!semana.isEmpty())
                .tieneCursos(!estudiante.getCursos().isEmpty())
                .proximaEvaluacion(evaluacionService.proximas(hoy, DIAS_HORIZONTE).stream().findFirst().orElse(null))
                .proximoBloque(bloqueRepository
                        .findFirstByEvaluacionCursoEstudianteIdAndFechaAfterAndCompletadoFalseAndReprogramadoFalseOrderByFechaAscHoraInicioAsc(
                                estudiante.getId(), hoy)
                        .orElse(null))
                .cursosSinSilabo(estudiante.getCursos().stream()
                        .filter(c -> c.getEvaluaciones().isEmpty())
                        .map(c -> c.getCodigo())
                        .toList())
                .build();
    }

    private List<CargaDiaDto> cargaPorDia(List<BloqueEstudio> bloques, LocalDate hoy, double horasMaximas) {
        List<CargaDiaDto> carga = new ArrayList<>();
        for (int i = 0; i < DIAS_ENTREGAS_SEMANA; i++) {
            LocalDate dia = hoy.plusDays(i);
            List<BloqueEstudio> delDia = bloques.stream().filter(b -> b.getFecha().equals(dia)).toList();
            carga.add(new CargaDiaDto(dia, sumarHoras(delDia, false), sumarHoras(delDia, true), horasMaximas, i == 0));
        }
        return carga;
    }

    private double sumarHoras(List<BloqueEstudio> bloques, boolean soloCompletados) {
        return bloques.stream()
                .filter(b -> !soloCompletados || b.isCompletado())
                .mapToDouble(BloqueEstudio::getDuracionHoras)
                .sum();
    }

    private String saludo(int hora) {
        if (hora < 12) return "Buenos días";
        if (hora < 19) return "Buenas tardes";
        return "Buenas noches";
    }

    private String primerNombre(String nombre) {
        return nombre == null || nombre.isBlank() ? "" : nombre.trim().split("\\s+")[0];
    }
}

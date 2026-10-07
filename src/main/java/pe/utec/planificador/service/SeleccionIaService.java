package pe.utec.planificador.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.ArchivoParaIa;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Elige qué IA usar según planificador.ia.proveedor:
 * "auto" usa los proveedores con API key en el orden de planificador.ia.orden y, si uno falla por algo
 * pasajero (saturación, límite de uso, documento muy grande para su plan), prueba con el siguiente.
 * "anthropic", "gemini" o "groq" fijan uno solo.
 */
@Slf4j
@Service
public class SeleccionIaService {

    private final List<ExtractorSilaboIa> extractores;
    private final String proveedor;

    public SeleccionIaService(List<ExtractorSilaboIa> extractores,
                              @Value("${planificador.ia.proveedor:auto}") String proveedor,
                              @Value("${planificador.ia.orden:gemini,groq,anthropic}") String orden) {
        List<String> prioridad = Arrays.stream(orden.split(",")).map(s -> s.strip().toLowerCase(Locale.ROOT)).toList();
        this.extractores = extractores.stream()
                .sorted(Comparator.comparingInt(e -> {
                    int posicion = prioridad.indexOf(e.getClave());
                    return posicion < 0 ? Integer.MAX_VALUE : posicion;
                }))
                .toList();
        this.proveedor = proveedor.strip().toLowerCase(Locale.ROOT);
    }

    /** Proveedores que se usarán, en orden (el primero es el principal; los demás, respaldo). */
    public List<ExtractorSilaboIa> disponibles() {
        return extractores.stream()
                .filter(e -> "auto".equals(proveedor) || e.getClave().equals(proveedor))
                .filter(ExtractorSilaboIa::estaConfigurado)
                .toList();
    }

    /** El proveedor principal, si hay alguno configurado. */
    public Optional<ExtractorSilaboIa> activo() {
        return disponibles().stream().findFirst();
    }

    /** Nombres de los proveedores de respaldo, ej. "Groq, Claude (Anthropic)". */
    public String respaldos() {
        return String.join(", ", disponibles().stream().skip(1).map(ExtractorSilaboIa::getNombre).toList());
    }

    public SilaboExtraidoDto extraer(String textoSilabo, LocalDate inicioCiclo) {
        return conRespaldo(ia -> ia.extraer(textoSilabo, inicioCiclo));
    }

    public HorarioMatriculaDto extraerHorario(ArchivoParaIa archivo) {
        return conRespaldo(ia -> ia.extraerHorario(archivo));
    }

    private <T> T conRespaldo(Function<ExtractorSilaboIa, T> tarea) {
        List<ExtractorSilaboIa> proveedores = disponibles();
        if (proveedores.isEmpty()) {
            throw new SilaboException("La IA no está configurada: define la variable de entorno "
                    + "GEMINI_API_KEY, GROQ_API_KEY o ANTHROPIC_API_KEY y reinicia la aplicación.");
        }
        SilaboException ultimo = null;
        for (ExtractorSilaboIa ia : proveedores) {
            try {
                return tarea.apply(ia);
            } catch (SilaboException e) {
                if (!e.isTemporal()) {
                    throw e;
                }
                log.warn("{} no pudo ahora ({}); se prueba con el siguiente proveedor", ia.getNombre(), e.getMessage());
                ultimo = e;
            }
        }
        throw proveedores.size() == 1 ? ultimo
                : SilaboException.temporal("Ninguna IA pudo responder ahora (" + ultimo.getMessage() + "). Espera un minuto y vuelve a intentar.", ultimo);
    }
}

package pe.utec.planificador.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pe.utec.planificador.dto.ArchivoParaIa;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Extracción con Google Gemini usando la API REST generateContent y salida estructurada (JSON Schema).
 * La API key se lee de la variable de entorno GEMINI_API_KEY.
 */
@Slf4j
@Service
public class ExtractorGeminiService implements ExtractorSilaboIa {

    static final String URL_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAXIMO_TOKENS = 32_000;
    /** Intentos por modelo cuando Gemini está saturado (503) o limita el uso (429). */
    private static final int INTENTOS_POR_MODELO = 2;

    private final RestClient cliente;
    private final ObjectMapper objectMapper;
    private final String modelo;
    /** Modelos alternativos, en orden, por si el principal está saturado. */
    private final List<String> modelosRespaldo;
    private final String apiKey;
    private final Duration pausaEntreIntentos;

    @Autowired
    public ExtractorGeminiService(RestClient.Builder constructor, ObjectMapper objectMapper,
                                  @Value("${planificador.ia.gemini.modelo}") String modelo,
                                  @Value("${planificador.ia.gemini.modelos-respaldo:}") String modelosRespaldo,
                                  @Value("${GEMINI_API_KEY:}") String apiKey) {
        this(constructor.baseUrl(URL_BASE).requestFactory(conTiempos()).build(), objectMapper,
                modelo, modelosRespaldo, apiKey, Duration.ofSeconds(2));
    }

    /** Constructor para pruebas: recibe el cliente HTTP ya armado. */
    ExtractorGeminiService(RestClient cliente, ObjectMapper objectMapper, String modelo, String modelosRespaldo,
                           String apiKey, Duration pausaEntreIntentos) {
        this.cliente = cliente;
        this.objectMapper = objectMapper;
        this.modelo = modelo;
        this.modelosRespaldo = modelosRespaldo == null ? List.of() : Arrays.stream(modelosRespaldo.split(","))
                .map(String::strip)
                .filter(m -> !m.isEmpty() && !m.equals(modelo))
                .toList();
        this.apiKey = apiKey;
        this.pausaEntreIntentos = pausaEntreIntentos;
    }

    private static SimpleClientHttpRequestFactory conTiempos() {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(Duration.ofSeconds(15));
        tiempos.setReadTimeout(Duration.ofMinutes(3)); // un sílabo largo puede tardar
        return tiempos;
    }

    @Override
    public String getClave() {
        return "gemini";
    }

    @Override
    public String getNombre() {
        return "Gemini (Google)";
    }

    @Override
    public String getModelo() {
        return modelo;
    }

    @Override
    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public SilaboExtraidoDto extraer(String textoSilabo, LocalDate inicioCiclo) {
        return llamar(construirSolicitud(textoSilabo, inicioCiclo), SilaboExtraidoDto.class);
    }

    @Override
    public HorarioMatriculaDto extraerHorario(ArchivoParaIa archivo) {
        return llamar(construirSolicitudHorario(archivo), HorarioMatriculaDto.class);
    }

    Map<String, Object> construirSolicitud(String textoSilabo, LocalDate inicioCiclo) {
        return solicitud(InstruccionesIa.SILABO,
                List.of(Map.of("text", InstruccionesIa.mensajeUsuario(textoSilabo, inicioCiclo))),
                EsquemasJson.silabo());
    }

    /** La imagen o el PDF viajan en base64 (inline_data) antes del texto. */
    Map<String, Object> construirSolicitudHorario(ArchivoParaIa archivo) {
        Map<String, Object> datos = Map.of("inline_data", Map.of(
                "mime_type", archivo.tipoMime(),
                "data", Base64.getEncoder().encodeToString(archivo.datos())));
        return solicitud(InstruccionesIa.HORARIO,
                List.of(datos, Map.of("text", InstruccionesIa.MENSAJE_HORARIO)),
                EsquemasJson.horario());
    }

    private Map<String, Object> solicitud(String instrucciones, List<Map<String, Object>> partes, Map<String, Object> esquema) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instrucciones))),
                "contents", List.of(Map.of("role", "user", "parts", partes)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", esquema,
                        "maxOutputTokens", MAXIMO_TOKENS));
    }

    /**
     * Prueba el modelo principal y, si Gemini está saturado, reintenta y luego usa los modelos de respaldo.
     * Los errores definitivos (clave inválida, solicitud incorrecta) no se reintentan; un modelo de respaldo
     * que no existe para la cuenta simplemente se salta.
     */
    private <T> T llamar(Map<String, Object> solicitud, Class<T> tipo) {
        List<String> modelos = new ArrayList<>();
        modelos.add(modelo);
        modelos.addAll(modelosRespaldo);
        SilaboException ultimoError = null;
        for (String modeloActual : modelos) {
            boolean esRespaldo = !modeloActual.equals(modelo);
            for (int intento = 1; intento <= INTENTOS_POR_MODELO; intento++) {
                try {
                    return llamarModelo(modeloActual, solicitud, tipo);
                } catch (RestClientResponseException e) {
                    if (esRespaldo && e.getStatusCode().value() == 404) {
                        log.warn("Modelo de respaldo {} no disponible para esta cuenta; se salta", modeloActual);
                        break;
                    }
                    if (!esTemporal(e)) {
                        throw traducirError(e, modeloActual);
                    }
                    log.warn("Gemini {} no disponible ({}), intento {} de {}", modeloActual,
                            e.getStatusCode().value(), intento, INTENTOS_POR_MODELO);
                    ultimoError = traducirError(e, modeloActual);
                    esperar(intento);
                }
            }
        }
        throw ultimoError;
    }

    private boolean esTemporal(RestClientResponseException e) {
        int estado = e.getStatusCode().value();
        return estado == 429 || estado == 500 || estado == 503;
    }

    private void esperar(int intento) {
        try {
            Thread.sleep(pausaEntreIntentos.toMillis() * intento);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T llamarModelo(String modeloActual, Map<String, Object> solicitud, Class<T> tipo) {
        try {
            JsonNode respuesta = cliente.post()
                    .uri("/models/{modelo}:generateContent", modeloActual)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .body(JsonNode.class);
            log.info("Respuesta de Gemini con el modelo {}", modeloActual);
            return interpretar(respuesta, tipo);
        } catch (ResourceAccessException e) {
            throw SilaboException.temporal("No hay conexión con Gemini o tardó demasiado. Revisa tu internet e inténtalo de nuevo.", e);
        }
    }

    <T> T interpretar(JsonNode respuesta, Class<T> tipo) {
        if (respuesta == null) {
            throw new SilaboException("Gemini no devolvió respuesta. Inténtalo de nuevo.");
        }
        String bloqueo = respuesta.path("promptFeedback").path("blockReason").asText("");
        if (!bloqueo.isEmpty()) {
            throw new SilaboException("Gemini bloqueó el documento (" + bloqueo + "). Verifica que sea el correcto.");
        }
        JsonNode candidato = respuesta.path("candidates").path(0);
        String motivo = candidato.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(motivo)) {
            throw new SilaboException("El documento es demasiado extenso para analizarlo de una vez.");
        }
        if (!motivo.isEmpty() && !"STOP".equals(motivo)) {
            throw new SilaboException("Gemini no pudo procesar este documento (" + motivo + ").");
        }
        StringBuilder json = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false)) { // se ignoran los resúmenes de razonamiento
                json.append(parte.path("text").asText(""));
            }
        }
        if (json.isEmpty()) {
            throw new SilaboException("Gemini no devolvió datos. Inténtalo de nuevo.");
        }
        try {
            log.info("Documento analizado con {} ({} tokens)", modelo,
                    respuesta.path("usageMetadata").path("totalTokenCount").asInt());
            return objectMapper.readValue(json.toString(), tipo);
        } catch (JsonProcessingException e) {
            log.warn("Respuesta de Gemini con JSON inválido: {}", e.getOriginalMessage());
            throw new SilaboException("La respuesta de Gemini no tuvo el formato esperado. Inténtalo de nuevo.", e);
        }
    }

    private SilaboException traducirError(RestClientResponseException e, String modeloActual) {
        String detalle = e.getResponseBodyAsString();
        int estado = e.getStatusCode().value();
        log.warn("Error de Gemini {}: {}", estado, detalle);
        if (detalle.contains("API_KEY_INVALID") || estado == 401 || estado == 403) {
            return new SilaboException("La API key de Gemini no es válida o no tiene permisos. "
                    + "Créala en aistudio.google.com y ponla en GEMINI_API_KEY.", e);
        }
        if (estado == 404) {
            return new SilaboException("El modelo \"" + modeloActual + "\" no existe en Gemini. Revisa planificador.ia.gemini.modelo.", e);
        }
        if (estado == 429) {
            return SilaboException.temporal("Se alcanzó el límite de uso de Gemini. Espera un minuto y vuelve a intentar.", e);
        }
        if (estado >= 500) {
            return SilaboException.temporal("Gemini tiene mucha demanda en este momento. Espera un minuto y vuelve a intentar.", e);
        }
        return new SilaboException("Gemini rechazó la solicitud (código " + estado + ").", e);
    }
}

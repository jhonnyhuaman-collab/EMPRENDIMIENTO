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
import java.util.*;

/**
 * Extracción con Groq (API compatible con OpenAI) y salida estructurada estricta (json_schema).
 * Texto: modelo principal (gpt-oss). Fotos de horario: modelo con visión (Qwen). Groq no lee PDF,
 * así que de un PDF de matrícula se envía el texto extraído con PDFBox.
 * La API key se lee de la variable de entorno GROQ_API_KEY.
 */
@Slf4j
@Service
public class ExtractorGroqService implements ExtractorSilaboIa {

    static final String URL_BASE = "https://api.groq.com/openai/v1";

    private final RestClient cliente;
    private final ObjectMapper objectMapper;
    private final LectorPdfService lectorPdf;
    private final String modelo;
    private final String modeloVision;
    private final int maximoTokens;
    private final int maximoTokensVision;
    private final String apiKey;

    @Autowired
    public ExtractorGroqService(RestClient.Builder constructor, ObjectMapper objectMapper, LectorPdfService lectorPdf,
                                @Value("${planificador.ia.groq.modelo}") String modelo,
                                @Value("${planificador.ia.groq.modelo-vision}") String modeloVision,
                                @Value("${planificador.ia.groq.maximo-tokens:6000}") int maximoTokens,
                                @Value("${planificador.ia.groq.maximo-tokens-vision:1000}") int maximoTokensVision,
                                @Value("${GROQ_API_KEY:}") String apiKey) {
        this(constructor.baseUrl(URL_BASE).requestFactory(conTiempos()).build(), objectMapper, lectorPdf,
                modelo, modeloVision, maximoTokens, maximoTokensVision, apiKey);
    }

    /** Constructor para pruebas: recibe el cliente HTTP ya armado. */
    ExtractorGroqService(RestClient cliente, ObjectMapper objectMapper, LectorPdfService lectorPdf, String modelo,
                         String modeloVision, int maximoTokens, int maximoTokensVision, String apiKey) {
        this.cliente = cliente;
        this.objectMapper = objectMapper;
        this.lectorPdf = lectorPdf;
        this.modelo = modelo;
        this.modeloVision = modeloVision;
        this.maximoTokens = maximoTokens;
        this.maximoTokensVision = maximoTokensVision;
        this.apiKey = apiKey;
    }

    private static SimpleClientHttpRequestFactory conTiempos() {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(Duration.ofSeconds(15));
        tiempos.setReadTimeout(Duration.ofMinutes(3));
        return tiempos;
    }

    @Override
    public String getClave() {
        return "groq";
    }

    @Override
    public String getNombre() {
        return "Groq";
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
        return llamar(modelo, maximoTokens, InstruccionesIa.SILABO,
                InstruccionesIa.mensajeUsuario(textoSilabo, inicioCiclo),
                EsquemasJson.silabo(), "silabo", SilaboExtraidoDto.class);
    }

    @Override
    public HorarioMatriculaDto extraerHorario(ArchivoParaIa archivo) {
        if (archivo.esPdf()) {
            String texto = lectorPdf.extraerTexto(archivo.datos());
            return llamar(modelo, maximoTokens, InstruccionesIa.HORARIO,
                    InstruccionesIa.MENSAJE_HORARIO + "\n\n<documento>\n" + texto + "\n</documento>",
                    EsquemasJson.horario(), "horario", HorarioMatriculaDto.class);
        }
        String imagen = "data:" + archivo.tipoMime() + ";base64," + Base64.getEncoder().encodeToString(archivo.datos());
        List<Map<String, Object>> contenido = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", imagen)),
                Map.of("type", "text", "text", InstruccionesIa.MENSAJE_HORARIO));
        return llamar(modeloVision, maximoTokensVision, InstruccionesIa.HORARIO, contenido,
                EsquemasJson.horario(), "horario", HorarioMatriculaDto.class);
    }

    Map<String, Object> construirSolicitud(String modeloActual, int tokens, String instrucciones, Object contenidoUsuario,
                                           Map<String, Object> esquema, String nombreEsquema) {
        return Map.of(
                "model", modeloActual,
                "messages", List.of(
                        Map.of("role", "system", "content", instrucciones),
                        Map.of("role", "user", "content", contenidoUsuario)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", nombreEsquema, "strict", true, "schema", cerrado(esquema))),
                "max_completion_tokens", tokens);
    }

    private <T> T llamar(String modeloActual, int tokens, String instrucciones, Object contenidoUsuario,
                         Map<String, Object> esquema, String nombreEsquema, Class<T> tipo) {
        try {
            JsonNode respuesta = cliente.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(construirSolicitud(modeloActual, tokens, instrucciones, contenidoUsuario, esquema, nombreEsquema))
                    .retrieve()
                    .body(JsonNode.class);
            return interpretar(respuesta, modeloActual, tipo);
        } catch (RestClientResponseException e) {
            throw traducirError(e, modeloActual);
        } catch (ResourceAccessException e) {
            throw SilaboException.temporal("No hay conexión con Groq o tardó demasiado.", e);
        }
    }

    <T> T interpretar(JsonNode respuesta, String modeloActual, Class<T> tipo) {
        JsonNode eleccion = respuesta == null ? null : respuesta.path("choices").path(0);
        if (eleccion == null || eleccion.isMissingNode()) {
            throw SilaboException.temporal("Groq no devolvió respuesta.");
        }
        if ("length".equals(eleccion.path("finish_reason").asText())) {
            throw SilaboException.temporal("La respuesta de Groq se cortó por el límite de su plan gratuito.");
        }
        String json = eleccion.path("message").path("content").asText("");
        if (json.isBlank()) {
            throw SilaboException.temporal("Groq no devolvió datos.");
        }
        try {
            log.info("Documento analizado con Groq {} ({} tokens)", modeloActual,
                    respuesta.path("usage").path("total_tokens").asInt());
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            log.warn("Respuesta de Groq con JSON inválido: {}", e.getOriginalMessage());
            throw SilaboException.temporal("La respuesta de Groq no tuvo el formato esperado.", e);
        }
    }

    private SilaboException traducirError(RestClientResponseException e, String modeloActual) {
        int estado = e.getStatusCode().value();
        String detalle = e.getResponseBodyAsString();
        log.warn("Error de Groq {}: {}", estado, detalle);
        if (estado == 401 || estado == 403) {
            return new SilaboException("La API key de Groq no es válida. Créala en console.groq.com y ponla en GROQ_API_KEY.", e);
        }
        if (estado == 404) {
            return new SilaboException("El modelo \"" + modeloActual + "\" no existe en Groq. Revisa planificador.ia.groq.modelo.", e);
        }
        if (estado == 413 || (estado == 429 && detalle.contains("Request too large"))) {
            return SilaboException.temporal("El documento es demasiado grande para el plan gratuito de Groq.", e);
        }
        if (estado == 429) {
            return SilaboException.temporal("Se alcanzó el límite por minuto de Groq. Espera un minuto y vuelve a intentar.", e);
        }
        if (estado >= 500 || detalle.contains("json_validate_failed")) {
            return SilaboException.temporal("Groq no pudo procesar el documento en este momento.", e);
        }
        return new SilaboException("Groq rechazó la solicitud (código " + estado + ").", e);
    }

    /** El modo estricto de Groq exige additionalProperties=false en cada objeto del esquema. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> cerrado(Map<String, Object> esquema) {
        Map<String, Object> copia = new LinkedHashMap<>();
        esquema.forEach((clave, valor) -> {
            if (valor instanceof Map<?, ?> mapa) {
                copia.put(clave, cerrado((Map<String, Object>) mapa));
            } else {
                copia.put(clave, valor);
            }
        });
        if ("object".equals(copia.get("type")) && copia.containsKey("properties")) {
            Map<String, Object> propiedades = new LinkedHashMap<>();
            ((Map<String, Object>) copia.get("properties")).forEach((nombre, sub) ->
                    propiedades.put(nombre, cerrado((Map<String, Object>) sub)));
            copia.put("properties", propiedades);
            copia.put("additionalProperties", false);
        }
        return copia;
    }
}

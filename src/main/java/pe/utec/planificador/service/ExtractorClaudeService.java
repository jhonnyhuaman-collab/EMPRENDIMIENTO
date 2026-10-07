package pe.utec.planificador.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.*;
import com.anthropic.models.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.ArchivoParaIa;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/** Extracción con Claude mediante el SDK oficial de Anthropic y salida estructurada. */
@Slf4j
@Service
public class ExtractorClaudeService implements ExtractorSilaboIa {

    private static final long MAXIMO_TOKENS = 16_000L;

    private final ObjectProvider<AnthropicClient> clienteAnthropic;
    private final String modelo;
    private final String apiKey;

    public ExtractorClaudeService(ObjectProvider<AnthropicClient> clienteAnthropic,
                                  @Value("${planificador.ia.anthropic.modelo}") String modelo,
                                  @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.clienteAnthropic = clienteAnthropic;
        this.modelo = modelo;
        this.apiKey = apiKey;
    }

    @Override
    public String getClave() {
        return "anthropic";
    }

    @Override
    public String getNombre() {
        return "Claude (Anthropic)";
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
        return ejecutar(construirParametros(textoSilabo, inicioCiclo));
    }

    @Override
    public HorarioMatriculaDto extraerHorario(ArchivoParaIa archivo) {
        return ejecutar(construirParametrosHorario(archivo));
    }

    StructuredMessageCreateParams<SilaboExtraidoDto> construirParametros(String textoSilabo, LocalDate inicioCiclo) {
        return MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAXIMO_TOKENS)
                .system(InstruccionesIa.SILABO)
                .outputConfig(SilaboExtraidoDto.class)
                .addUserMessage(InstruccionesIa.mensajeUsuario(textoSilabo, inicioCiclo))
                .build();
    }

    StructuredMessageCreateParams<HorarioMatriculaDto> construirParametrosHorario(ArchivoParaIa archivo) {
        return MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAXIMO_TOKENS)
                .system(InstruccionesIa.HORARIO)
                .outputConfig(HorarioMatriculaDto.class)
                .addUserMessageOfBlockParams(List.of(
                        bloqueArchivo(archivo),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(InstruccionesIa.MENSAJE_HORARIO).build())))
                .build();
    }

    /** La imagen o el PDF van antes del texto, como recomienda la API. */
    private ContentBlockParam bloqueArchivo(ArchivoParaIa archivo) {
        String base64 = Base64.getEncoder().encodeToString(archivo.datos());
        if (archivo.esPdf()) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(base64).build())
                    .build());
        }
        return ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .data(base64)
                        .mediaType(Base64ImageSource.MediaType.of(archivo.tipoMime()))
                        .build())
                .build());
    }

    /** Envía la solicitud y traduce los errores de la API a mensajes en español. */
    private <T> T ejecutar(StructuredMessageCreateParams<T> parametros) {
        try {
            StructuredMessage<T> respuesta = clienteAnthropic.getObject().messages().create(parametros);
            validarMotivoDeParada(respuesta);
            log.info("Documento analizado con {} ({} tokens de entrada, {} de salida)", modelo,
                    respuesta.usage().inputTokens(), respuesta.usage().outputTokens());
            return respuesta.content().stream()
                    .flatMap(bloque -> bloque.text().stream())
                    .map(texto -> texto.text())
                    .findFirst()
                    .orElseThrow(() -> new SilaboException("La IA no devolvió datos. Inténtalo de nuevo."));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new SilaboException("La API key de Anthropic no es válida o no tiene permisos. "
                    + "Recuerda que debe ser una clave de console.anthropic.com (empieza con sk-ant-).", e);
        } catch (NotFoundException e) {
            throw new SilaboException("El modelo \"" + modelo + "\" no existe. Revisa planificador.ia.anthropic.modelo.", e);
        } catch (RateLimitException e) {
            throw SilaboException.temporal("Se alcanzó el límite de uso de Claude. Espera un minuto y vuelve a intentar.", e);
        } catch (BadRequestException e) {
            log.warn("Solicitud rechazada por la API de Anthropic: {}", e.getMessage());
            throw new SilaboException("La IA rechazó la solicitud: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw SilaboException.temporal("Claude no está disponible en este momento. Inténtalo más tarde.", e);
        } catch (AnthropicIoException e) {
            throw SilaboException.temporal("No hay conexión con Claude. Revisa tu internet.", e);
        } catch (AnthropicException e) {
            log.error("Error inesperado con la IA de Anthropic", e);
            throw new SilaboException("No se pudo analizar el documento con la IA.", e);
        }
    }

    private void validarMotivoDeParada(StructuredMessage<?> respuesta) {
        respuesta.stopReason().ifPresent(motivo -> {
            if (motivo.equals(StopReason.MAX_TOKENS)) {
                throw new SilaboException("El documento es demasiado extenso para analizarlo de una vez.");
            }
            if (motivo.equals(StopReason.REFUSAL)) {
                throw new SilaboException("La IA no pudo procesar este documento. Verifica que sea el correcto.");
            }
        });
    }
}

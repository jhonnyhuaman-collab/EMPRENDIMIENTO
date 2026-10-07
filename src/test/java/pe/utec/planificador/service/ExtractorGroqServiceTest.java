package pe.utec.planificador.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.utec.planificador.dto.ArchivoParaIa;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Simula la API de Groq (sin internet). */
class ExtractorGroqServiceTest {

    private static final String URL = ExtractorGroqService.URL_BASE + "/chat/completions";

    private MockRestServiceServer servidor;
    private ExtractorGroqService groq;
    private final LectorPdfService lectorPdf = mock(LectorPdfService.class);

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl(ExtractorGroqService.URL_BASE);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        groq = new ExtractorGroqService(constructor.build(), new ObjectMapper(), lectorPdf,
                "openai/gpt-oss-120b", "qwen/qwen3.8-27b", 6000, 1000, "gsk_prueba");
    }

    @Test
    void leeUnSilaboConEsquemaEstricto() {
        String silabo = "{\"codigo\":\"MT5307\",\"nombre\":\"Control\",\"creditos\":4,\"dificultadEstimada\":4,"
                + "\"temas\":[],\"evaluaciones\":[],\"horarios\":[],\"observaciones\":\"\"}";
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk_prueba"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.evaluaciones.items.additionalProperties").value(false))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("2026-08-10")))
                .andRespond(withSuccess(respuesta(silabo, "stop"), MediaType.APPLICATION_JSON));

        assertThat(groq.extraer("texto", LocalDate.of(2026, 8, 10)).codigo()).isEqualTo("MT5307");
        servidor.verify();
    }

    @Test
    void lasFotosVanAlModeloConVision() {
        String horario = "{\"cursos\":[{\"codigo\":\"MT5307\",\"nombre\":\"Control\",\"creditos\":4,"
                + "\"sesiones\":[{\"dia\":\"MONDAY\",\"horaInicio\":\"14:00\",\"horaFin\":\"16:00\","
                + "\"detalle\":\"\",\"frecuencia\":\"SEMANA_A\"}]}],\"observaciones\":\"\"}";
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("qwen/qwen3.8-27b"))
                .andExpect(jsonPath("$.max_completion_tokens").value(1000))
                .andExpect(jsonPath("$.messages[1].content[0].image_url.url").value(startsWith("data:image/png;base64,AQID")))
                .andRespond(withSuccess(respuesta(horario, "stop"), MediaType.APPLICATION_JSON));

        var leido = groq.extraerHorario(new ArchivoParaIa(new byte[]{1, 2, 3}, "image/png", "horario.png"));
        assertThat(leido.cursos().get(0).sesiones().get(0).dia()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void unPdfDeMatriculaSeEnviaComoTexto() {
        when(lectorPdf.extraerTexto(new byte[]{9})).thenReturn("MT5307 Control Inteligente Lunes 14:00-16:00");
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("Lunes 14:00-16:00")))
                .andRespond(withSuccess(respuesta("{\"cursos\":[],\"observaciones\":\"\"}", "stop"), MediaType.APPLICATION_JSON));

        assertThat(groq.extraerHorario(new ArchivoParaIa(new byte[]{9}, "application/pdf", "matricula.pdf")).cursos()).isEmpty();
    }

    @Test
    void unDocumentoMuyGrandeParaElPlanGratuitoEsTemporal() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> groq.extraer("texto", LocalDate.of(2026, 8, 10)))
                .isInstanceOf(SilaboException.class)
                .hasMessageContaining("demasiado grande")
                .matches(e -> ((SilaboException) e).isTemporal());
    }

    @Test
    void unaClaveInvalidaNoEsTemporal() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> groq.extraer("texto", LocalDate.of(2026, 8, 10)))
                .hasMessageContaining("API key de Groq")
                .matches(e -> !((SilaboException) e).isTemporal());
    }

    @Test
    void cierraTodosLosObjetosDelEsquema() {
        Map<String, Object> cerrado = ExtractorGroqService.cerrado(EsquemasJson.horario());
        assertThat(cerrado).containsEntry("additionalProperties", false);
        assertThat(cerrado.toString()).doesNotContain("additionalProperties=true");
    }

    private String respuesta(String contenido, String motivo) {
        try {
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                    + new ObjectMapper().writeValueAsString(contenido) + "},\"finish_reason\":\"" + motivo + "\"}],"
                    + "\"usage\":{\"total_tokens\":900}}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package pe.utec.planificador.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;
import pe.utec.planificador.model.TipoEvaluacion;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Simula la API de Gemini (sin internet) para verificar la solicitud y la lectura de la respuesta. */
class ExtractorGeminiServiceTest {

    private static final String URL = ExtractorGeminiService.URL_BASE + "/models/gemini-3.8-flash:generateContent";
    private static final LocalDate INICIO = LocalDate.of(2026, 8, 17);

    private MockRestServiceServer servidor;
    private ExtractorGeminiService gemini;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl(ExtractorGeminiService.URL_BASE);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        gemini = new ExtractorGeminiService(constructor.build(), new ObjectMapper(), "gemini-3.8-flash",
                "gemini-2.5-flash, gemini-3.7-flash", "clave-de-prueba", java.time.Duration.ZERO);
    }

    @Test
    void enviaLaSolicitudConEsquemaYLeeLaRespuesta() {
        String silabo = """
                {"codigo":"MT5307","nombre":"Control Inteligente","creditos":4,"dificultadEstimada":4,
                 "temas":[{"unidad":1,"numero":"6.1","titulo":"Introducción"}],
                 "evaluaciones":[{"nombre":"EC1","tipo":"EXAMEN","semana":6,"pesoComponente":60,
                                  "pesoDentroDelComponente":33,"temas":["6.1"]}],
                 "horarios":[{"dia":"MONDAY","horaInicio":"14:00","horaFin":"16:00"}],
                 "observaciones":"ok"}""";
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.evaluaciones.items.properties.tipo.enum[1]").value("EXAMEN"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString("2026-08-17")))
                .andRespond(withSuccess(respuesta(silabo, "STOP"), MediaType.APPLICATION_JSON));

        SilaboExtraidoDto resultado = gemini.extraer("texto del sílabo", INICIO);

        assertThat(resultado.codigo()).isEqualTo("MT5307");
        assertThat(resultado.evaluaciones().get(0).tipo()).isEqualTo(TipoEvaluacion.EXAMEN);
        assertThat(resultado.evaluaciones().get(0).pesoDentroDelComponente()).isEqualTo(33);
        assertThat(resultado.horarios().get(0).dia()).isEqualTo(DayOfWeek.MONDAY);
        servidor.verify();
    }

    @Test
    void enviaLaFotoDelHorarioComoInlineData() {
        String horario = """
                {"cursos":[{"codigo":"MT5307","nombre":"Control Inteligente","creditos":4,
                  "sesiones":[{"dia":"MONDAY","horaInicio":"14:00","horaFin":"16:00","detalle":"Teoría · A-505"}]}],
                 "observaciones":""}""";
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].inline_data.mime_type").value("image/png"))
                .andExpect(jsonPath("$.contents[0].parts[0].inline_data.data").value("AQID")) // base64 de {1,2,3}
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.cursos.type").value("array"))
                .andRespond(withSuccess(respuesta(horario, "STOP"), MediaType.APPLICATION_JSON));

        var resultado = gemini.extraerHorario(new pe.utec.planificador.dto.ArchivoParaIa(new byte[]{1, 2, 3}, "image/png", "horario.png"));

        assertThat(resultado.cursos()).singleElement().satisfies(c -> {
            assertThat(c.codigo()).isEqualTo("MT5307");
            assertThat(c.sesiones().get(0).dia()).isEqualTo(DayOfWeek.MONDAY);
        });
        servidor.verify();
    }

    @Test
    void siElModeloEstaSaturadoReintentaYUsaElDeRespaldo() {
        String inexistente = ExtractorGeminiService.URL_BASE + "/models/gemini-2.5-flash:generateContent";
        String respaldo = ExtractorGeminiService.URL_BASE + "/models/gemini-3.7-flash:generateContent";
        String silabo = "{\"codigo\":\"MT5307\",\"nombre\":\"Control\",\"creditos\":4,\"dificultadEstimada\":4,"
                + "\"temas\":[],\"evaluaciones\":[],\"horarios\":[],\"observaciones\":\"\"}";
        servidor.expect(ExpectedCount.twice(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo(inexistente)).andRespond(withStatus(HttpStatus.NOT_FOUND)); // se salta
        servidor.expect(requestTo(respaldo))
                .andRespond(withSuccess(respuesta(silabo, "STOP"), MediaType.APPLICATION_JSON));

        assertThat(gemini.extraer("texto", INICIO).codigo()).isEqualTo("MT5307");
        servidor.verify();
    }

    @Test
    void siTodosEstanSaturadosLoExplica() {
        servidor.expect(ExpectedCount.manyTimes(), anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> gemini.extraer("texto", INICIO)).isInstanceOf(SilaboException.class)
                .hasMessageContaining("mucha demanda");
    }

    @Test
    void avisaSiLaRespuestaSeCorto() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(respuesta("{\"codigo\":", "MAX_TOKENS"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gemini.extraer("texto", INICIO)).isInstanceOf(SilaboException.class)
                .hasMessageContaining("demasiado extenso");
    }

    @Test
    void explicaQueLaClaveNoEsValida() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
        assertThatThrownBy(() -> gemini.extraer("texto", INICIO)).isInstanceOf(SilaboException.class)
                .hasMessageContaining("API key de Gemini no es válida");
    }

    @Test
    void explicaQueElModeloNoExiste() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> gemini.extraer("texto", INICIO)).isInstanceOf(SilaboException.class)
                .hasMessageContaining("no existe en Gemini");
    }

    private String respuesta(String texto, String motivo) {
        try {
            String textoEscapado = new ObjectMapper().writeValueAsString(texto);
            return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + textoEscapado
                    + "}]},\"finishReason\":\"" + motivo + "\"}],\"usageMetadata\":{\"totalTokenCount\":1200}}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

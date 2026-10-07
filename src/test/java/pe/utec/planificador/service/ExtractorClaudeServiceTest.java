package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** No llama a la API: verifica que la solicitud (y el esquema JSON derivado de los DTO) se construya bien. */
class ExtractorClaudeServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void construyeLaSolicitudConSalidaEstructurada() {
        ExtractorClaudeService extractor = new ExtractorClaudeService(mock(ObjectProvider.class), "claude-sonnet-5", "");

        var parametros = extractor.construirParametros("Sílabo de prueba", LocalDate.of(2026, 8, 17));

        assertThat(parametros.rawParams().model().asString()).isEqualTo("claude-sonnet-5");
        assertThat(parametros.rawParams().outputConfig()).isPresent();
        assertThat(parametros.rawParams().messages().get(0).toString()).contains("2026-08-17").contains("Sílabo de prueba");
    }
}

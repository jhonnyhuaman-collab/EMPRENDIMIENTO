package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeleccionIaServiceTest {

    private final ExtractorClaudeService claude = mock(ExtractorClaudeService.class);
    private final ExtractorGeminiService gemini = mock(ExtractorGeminiService.class);
    private final ExtractorGroqService groq = mock(ExtractorGroqService.class);

    SeleccionIaServiceTest() {
        when(claude.getClave()).thenReturn("anthropic");
        when(gemini.getClave()).thenReturn("gemini");
        when(groq.getClave()).thenReturn("groq");
        when(groq.getNombre()).thenReturn("Groq");
    }

    private SeleccionIaService seleccion(String proveedor) {
        return new SeleccionIaService(List.of(claude, groq, gemini), proveedor, "gemini, groq, anthropic");
    }

    @Test
    void enModoAutoRespetaElOrdenConfigurado() {
        when(gemini.estaConfigurado()).thenReturn(true);
        when(groq.estaConfigurado()).thenReturn(true);
        when(claude.estaConfigurado()).thenReturn(true);
        assertThat(seleccion("auto").activo()).containsSame(gemini);
        assertThat(seleccion("auto").disponibles()).containsExactly(gemini, groq, claude);
    }

    @Test
    void usaSoloLosQueTienenClave() {
        when(groq.estaConfigurado()).thenReturn(true);
        assertThat(seleccion("auto").activo()).containsSame(groq);
        assertThat(seleccion("auto").respaldos()).isEmpty();
    }

    @Test
    void respetaElProveedorElegido() {
        when(gemini.estaConfigurado()).thenReturn(true);
        when(groq.estaConfigurado()).thenReturn(true);
        assertThat(seleccion("Groq").disponibles()).containsExactly(groq);
    }

    @Test
    void siGeminiEstaSaturadoUsaGroq() {
        when(gemini.estaConfigurado()).thenReturn(true);
        when(groq.estaConfigurado()).thenReturn(true);
        when(gemini.extraer(any(), any())).thenThrow(SilaboException.temporal("Gemini tiene mucha demanda"));
        var esperado = new pe.utec.planificador.dto.ia.SilaboExtraidoDto("MT5307", "Control", 4, 4,
                List.of(), List.of(), List.of(), "");
        when(groq.extraer(any(), any())).thenReturn(esperado);

        assertThat(seleccion("auto").extraer("texto", null)).isSameAs(esperado);
        assertThat(seleccion("auto").respaldos()).isEqualTo("Groq");
    }

    @Test
    void unErrorDefinitivoNoPasaAlSiguiente() {
        when(gemini.estaConfigurado()).thenReturn(true);
        when(groq.estaConfigurado()).thenReturn(true);
        when(gemini.extraer(any(), any())).thenThrow(new SilaboException("La API key de Gemini no es válida"));

        assertThatThrownBy(() -> seleccion("auto").extraer("texto", null)).hasMessageContaining("no es válida");
        verify(groq, never()).extraer(any(), any());
    }

    @Test
    void sinClavesExplicaQueHacer() {
        assertThat(seleccion("auto").activo()).isEmpty();
        assertThatThrownBy(() -> seleccion("auto").extraer("texto", null)).isInstanceOf(SilaboException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }
}

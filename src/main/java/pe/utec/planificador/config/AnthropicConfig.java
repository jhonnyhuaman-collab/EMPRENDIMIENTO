package pe.utec.planificador.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Cliente oficial de Anthropic. La API key se lee de la variable de entorno ANTHROPIC_API_KEY
 * (nunca del código ni de application.properties). Se crea recién cuando se usa la IA.
 */
@Configuration
public class AnthropicConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public AnthropicClient clienteAnthropic() {
        return AnthropicOkHttpClient.fromEnv();
    }
}

package pe.utec.planificador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Reloj con la zona horaria de Lima, para que "hoy" no dependa del servidor. */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${planificador.zona-horaria:America/Lima}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}

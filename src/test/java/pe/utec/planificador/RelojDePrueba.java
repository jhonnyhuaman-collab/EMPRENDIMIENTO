package pe.utec.planificador;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Fija "hoy" en el domingo 04/10/2026 a las 08:00 (semana 7 del ciclo) para que las pruebas sean repetibles. */
@TestConfiguration
public class RelojDePrueba {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 4, 8, 0);

    @Bean
    @Primary
    public Clock relojFijo() {
        return Clock.fixed(AHORA.atZone(LIMA).toInstant(), LIMA);
    }
}

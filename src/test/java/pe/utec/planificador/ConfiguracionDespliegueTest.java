package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionDespliegueTest {
    @Test
    void blueprintSoloCreaUnServidorGratuitoSinBaseDePago() throws Exception {
        Map<?, ?> blueprint;
        try (var archivo = Files.newBufferedReader(Path.of("render.yaml"))) {
            blueprint = new Yaml().load(archivo);
        }
        var servicios = (List<?>) blueprint.get("services");
        assertThat(servicios).hasSize(1);
        var servicio = (Map<?, ?>) servicios.get(0);
        assertThat(servicio.get("plan")).isEqualTo("free");
        assertThat(servicio.get("runtime")).isEqualTo("docker");
        assertThat(servicio.get("healthCheckPath")).isEqualTo("/actuator/health");
        assertThat(blueprint.get("databases")).isNull();
        var variables = (List<?>) servicio.get("envVars");
        for (var clave : List.of("JDBC_DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD")) {
            var variable = variables.stream().map(v -> (Map<?, ?>) v)
                    .filter(v -> clave.equals(v.get("key"))).findFirst().orElseThrow();
            assertThat(variable.get("sync")).isEqualTo(false);
            assertThat(variable.get("value")).isNull();
        }
    }

    @Test
    void produccionExigeCredencialesPostgresTlsYNoPublicaDetalles() throws Exception {
        var propiedades = new Properties();
        try (var archivo = getClass().getResourceAsStream("/application-prod.properties")) {
            assertThat(archivo).isNotNull();
            propiedades.load(archivo);
        }
        assertThat(propiedades.getProperty("spring.datasource.url")).isEqualTo("${JDBC_DATABASE_URL}");
        assertThat(propiedades.getProperty("spring.datasource.username")).isEqualTo("${DATABASE_USERNAME}");
        assertThat(propiedades.getProperty("spring.datasource.password")).isEqualTo("${DATABASE_PASSWORD}");
        assertThat(propiedades.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
        assertThat(propiedades.getProperty("spring.datasource.hikari.data-source-properties.sslmode"))
                .isEqualTo("verify-full");
        assertThat(propiedades.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(propiedades.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(propiedades.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(propiedades.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(propiedades.getProperty("planificador.datos-ejemplo")).isEqualTo("false");
    }
}

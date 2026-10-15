package pe.utec.planificador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.repository.EstudianteRepository;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pwa;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class PwaTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EstudianteRepository estudiantes;

    @Test
    void manifiestoPublicoPermiteInstalarSinUnaSesionPrevia() throws Exception {
        var response = mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
                .andExpect(jsonPath("$.display").value("standalone"))
                .andExpect(jsonPath("$.start_url").value("/"))
                .andExpect(jsonPath("$.scope").value("/"))
                .andReturn().getResponse();
        JsonNode manifest = json.readTree(response.getContentAsByteArray());
        assertThat(manifest.get("id").asText()).isEqualTo("/");
        for (JsonNode icon : manifest.get("icons")) {
            assertThat(icon.get("src").asText()).endsWith("-v2.png");
            byte[] bytes = mvc.perform(get(icon.get("src").asText()))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andReturn().getResponse().getContentAsByteArray();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertThat(image).isNotNull();
            assertThat(image.getColorModel().hasAlpha()).isFalse();
            assertThat(icon.get("sizes").asText()).isEqualTo(image.getWidth() + "x" + image.getHeight());
        }
    }

    @Test
    void workerYPantallaSinConexionSonPublicosYNoRetienenElWorkerEnCacheHttp() throws Exception {
        mvc.perform(get("/sw.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(content().string(containsString("planificador-publico-v2")));
        mvc.perform(get("/offline.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sin conexi")));
        mvc.perform(get("/images/pwa/apple-touch-icon-v2.png"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    @Test
    void loginYRegistroConservanSusFormulariosYMetadatosDeInstalacion() throws Exception {
        for (String route : new String[]{"/login", "/registro"}) {
            mvc.perform(get(route)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("rel=\"manifest\"")))
                    .andExpect(content().string(containsString("apple-touch-icon")))
                    .andExpect(content().string(containsString("/js/pwa-")))
                    .andExpect(content().string(containsString("icon-192-v2-")))
                    .andExpect(content().string(containsString("/css/marca-")))
                    .andExpect(content().string(containsString("Crear cuenta")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
    }

    @Test
    void instalarNoAbreLasRutasPrivadasNiDesactivaCsrf() throws Exception {
        mvc.perform(get("/agenda")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/api/eventos")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/plan/generar")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pwa@utec.edu.pe")
    void lasPantallasPrivadasTambienIncluyenElManifiesto() throws Exception {
        var estudiante = estudiantes.findFirstByOrderByIdAsc().orElseThrow();
        estudiante.setEmail("pwa@utec.edu.pe");
        estudiantes.save(estudiante);
        mvc.perform(get("/configuracion")).andExpect(status().isOk())
                .andExpect(content().string(containsString("rel=\"manifest\"")))
                .andExpect(content().string(containsString("marca-logo")))
                .andExpect(content().string(containsString("icon-192-v2-")))
                .andExpect(content().string(containsString("pwa-instalar")));
    }
}

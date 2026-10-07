package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.RelojDePrueba;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ics;DB_CLOSE_DELAY=-1", "planificador.datos-ejemplo=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(RelojDePrueba.class)
@Transactional
class ExportadorIcsServiceTest {

    @Autowired private ExportadorIcsService exportador;
    @Autowired private MockMvc mvc;

    @Test
    void generaUnCalendarioValido() {
        String ics = exportador.generar(RelojDePrueba.AHORA, RelojDePrueba.LIMA);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(contar(ics, "BEGIN:VEVENT")).isEqualTo(contar(ics, "END:VEVENT")).isGreaterThan(20);
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n"); // solo saltos CRLF
        // EC1 de MT5307: semana 6, lunes 14/09/2026, evento de día completo
        assertThat(desplegar(ics)).contains("SUMMARY:📌 Control Inteligente · EC1 (19.8%)").contains("DTSTART;VALUE=DATE:20260914");
        // Los bloques se exportan en UTC (Lima = UTC-5)
        assertThat(ics).containsPattern("DTSTART:202610\\d\\dT\\d{6}Z");
    }

    @Test
    void lasLineasNoSuperan75Octetos() {
        String ics = exportador.generar(RelojDePrueba.AHORA, RelojDePrueba.LIMA);
        assertThat(Arrays.stream(ics.split("\r\n")))
                .allSatisfy(l -> assertThat(l.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
    }

    @Test
    void escapaCaracteresEspeciales() {
        assertThat(ExportadorIcsService.escapar("a,b;c\\d\ne")).isEqualTo("a\\,b\\;c\\\\d\\ne");
    }

    @Test
    void descargaElArchivoDesdeLaWeb() throws Exception {
        mvc.perform(get("/plan/exportar.ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(header().string("Content-Disposition", containsString("plan-de-estudio.ics")));
    }

    /** Une las líneas plegadas (RFC 5545 §3.1) para buscar texto completo. */
    private String desplegar(String ics) {
        return ics.replace("\r\n ", "");
    }

    private int contar(String texto, String patron) {
        Matcher m = Pattern.compile(Pattern.quote(patron)).matcher(texto);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}

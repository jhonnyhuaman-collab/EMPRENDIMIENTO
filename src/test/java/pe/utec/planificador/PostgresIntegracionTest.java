package pe.utec.planificador;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.repository.CursoRepository;
import pe.utec.planificador.repository.EstudianteRepository;
import pe.utec.planificador.repository.EvaluacionRepository;

import javax.sql.DataSource;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Import(RelojDePrueba.class)
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@Transactional
class PostgresIntegracionTest {
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired Environment entorno;
    @Autowired EstudianteRepository estudiantes;
    @Autowired CursoRepository cursos;
    @Autowired EvaluacionRepository evaluaciones;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv("POSTGRES_TEST_USER"));
        propiedades.add("spring.datasource.password", () -> System.getenv("POSTGRES_TEST_PASSWORD"));
        // El PostgreSQL efimero de CI solo escucha en la red local, sin TLS.
        propiedades.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "disable");
    }

    @Test
    void perfilDeProduccionUsaPostgresYSesionSegura() throws Exception {
        try (var conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(entorno.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(entorno.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(entorno.getProperty("planificador.ia.proveedor")).isEqualTo("groq");
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void registroCursoYAgendaPersistenEnPostgresYSeSeparanPorCuenta() throws Exception {
        mvc.perform(post("/registro").with(csrf())
                        .param("email", "ana.postgres@utec.edu.pe").param("password", "PruebaPostgres123"))
                .andExpect(redirectedUrl("/login?registrado"));
        mvc.perform(post("/registro").with(csrf())
                        .param("email", "luis.postgres@utec.edu.pe").param("password", "PruebaPostgres456"))
                .andExpect(redirectedUrl("/login?registrado"));
        var ana = estudiantes.findByEmailIgnoreCase("ana.postgres@utec.edu.pe").orElseThrow();
        var luis = estudiantes.findByEmailIgnoreCase("luis.postgres@utec.edu.pe").orElseThrow();
        assertThat(ana.getId()).isNotEqualTo(luis.getId());
        assertThat(ana.getPasswordHash()).startsWith("$2");

        mvc.perform(post("/silabos/guardar").with(user(ana.getEmail())).with(csrf())
                        .param("codigo", "PG101").param("nombre", "Curso PostgreSQL")
                        .param("creditos", "4").param("dificultad", "3").param("color", "#235B4E")
                        .param("evaluaciones[0].nombre", "Examen final")
                        .param("evaluaciones[0].tipo", "EXAMEN").param("evaluaciones[0].semana", "12")
                        .param("evaluaciones[0].dia", "MONDAY").param("evaluaciones[0].pesoEnNotaFinal", "100")
                        .param("evaluaciones[0].horasEstimadas", "6"))
                .andExpect(redirectedUrl("/cursos"));
        var curso = cursos.findByEstudianteIdOrderByCodigoAsc(ana.getId()).get(0);
        mvc.perform(post("/agenda").with(user(ana.getEmail())).with(csrf())
                        .param("cursoId", curso.getId().toString()).param("nombre", "Entrega PostgreSQL")
                        .param("tipo", "TAREA").param("fecha", "2026-10-15")
                        .param("horaEntrega", "23:59").param("notas", "Persistencia de prueba"))
                .andExpect(redirectedUrl("/agenda"));
        var tarea = evaluaciones.findByCursoEstudianteIdOrderByFechaAsc(ana.getId()).stream()
                .filter(e -> e.getNombre().equals("Entrega PostgreSQL")).findFirst().orElseThrow();
        assertThat(tarea.getHoraEntrega()).isEqualTo(LocalTime.of(23, 59));
        assertThat(tarea.getBloques()).isNotEmpty();
        mvc.perform(get("/agenda").with(user(ana.getEmail())))
                .andExpect(content().string(containsString("Entrega PostgreSQL")));
        mvc.perform(get("/cursos").with(user(luis.getEmail())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Curso PostgreSQL"))));
        mvc.perform(post("/agenda/" + tarea.getId() + "/completar").with(user(luis.getEmail())).with(csrf()))
                .andExpect(status().isNotFound());
    }
}

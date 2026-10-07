package pe.utec.planificador.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ReplanificacionInterceptor replanificacionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Solo pantallas: no en la API, archivos estáticos ni la consola H2
        registry.addInterceptor(replanificacionInterceptor)
                .addPathPatterns("/", "/calendario", "/agenda", "/horario", "/cursos", "/evaluaciones/**");
    }
}

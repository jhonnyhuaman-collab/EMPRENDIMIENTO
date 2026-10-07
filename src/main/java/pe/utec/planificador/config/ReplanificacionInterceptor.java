package pe.utec.planificador.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import pe.utec.planificador.service.ReplanificacionService;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Antes de mostrar una pantalla, revisa si el estudiante se atrasó y, si es así, replanifica.
 * El resumen se muestra como aviso en la pantalla.
 */
@Component
@RequiredArgsConstructor
public class ReplanificacionInterceptor implements HandlerInterceptor {

    static final String ATRIBUTO = "replanificacion";

    private final ReplanificacionService replanificacionService;
    private final Clock reloj;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("GET".equals(request.getMethod())) {
            replanificacionService.replanificarSiHayAtrasos(LocalDateTime.now(reloj))
                    .ifPresent(r -> request.setAttribute(ATRIBUTO, r));
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        Object resultado = request.getAttribute(ATRIBUTO);
        if (resultado != null && modelAndView != null && !modelAndView.getViewName().startsWith("redirect:")) {
            modelAndView.addObject(ATRIBUTO, resultado);
        }
    }
}

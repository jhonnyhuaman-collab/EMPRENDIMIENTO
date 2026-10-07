package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pe.utec.planificador.service.BloqueEstudioService;

import java.time.Clock;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class BloqueEstudioController {

    private final BloqueEstudioService bloqueEstudioService;
    private final Clock reloj;

    @PostMapping("/bloques/{id}/alternar")
    public String alternar(@PathVariable Long id, @RequestParam(defaultValue = "/") String volverA) {
        bloqueEstudioService.alternarCompletado(id, LocalDateTime.now(reloj));
        return "redirect:" + RutasInternas.segura(volverA);
    }
}

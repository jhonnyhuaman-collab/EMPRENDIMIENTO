package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.utec.planificador.service.PanelHoyService;

import java.time.Clock;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class HoyController {

    private final PanelHoyService panelHoyService;
    private final Clock reloj;

    @GetMapping("/")
    public String hoy(Model model) {
        model.addAttribute("panel", panelHoyService.construirPanel(LocalDateTime.now(reloj)));
        return "hoy";
    }
}

package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pe.utec.planificador.service.GuiaEvaluacionService;

import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class EvaluacionController {

    private final GuiaEvaluacionService guiaEvaluacionService;
    private final Clock reloj;

    @GetMapping("/evaluaciones/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("guia", guiaEvaluacionService.construirGuia(id, LocalDate.now(reloj)));
        return "evaluacion-detalle";
    }
}

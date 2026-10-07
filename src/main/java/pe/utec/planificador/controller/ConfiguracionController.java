package pe.utec.planificador.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.dto.formulario.ConfiguracionFormulario;
import pe.utec.planificador.service.ConfiguracionService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;
    private final pe.utec.planificador.service.PreparacionSilaboService preparacionSilabo;
    private final Clock reloj;

    @GetMapping("/configuracion")
    public String ver(Model model) {
        return mostrar(configuracionService.obtener(), model);
    }

    @PostMapping("/configuracion")
    public String guardar(@Valid @ModelAttribute("configuracion") ConfiguracionFormulario configuracion,
                          BindingResult errores, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            return mostrar(configuracion, model);
        }
        redireccion.addFlashAttribute("resultadoPlan", configuracionService.guardar(configuracion, LocalDateTime.now(reloj)));
        redireccion.addFlashAttribute("configuracionGuardada", true);
        return "redirect:/configuracion";
    }

    private String mostrar(ConfiguracionFormulario configuracion, Model model) {
        model.addAttribute("configuracion", configuracion);
        model.addAttribute("hoy", LocalDate.now(reloj));
        model.addAttribute("semanaActual", configuracionService.semanaActual(LocalDate.now(reloj)));
        model.addAttribute("diasSemana", preparacionSilabo.opcionesDias());
        return "configuracion";
    }
}

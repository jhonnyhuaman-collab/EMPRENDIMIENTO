package pe.utec.planificador.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import pe.utec.planificador.dto.formulario.AccesoFormulario;
import pe.utec.planificador.service.AccesoService;
import pe.utec.planificador.service.SilaboException;

@Controller
@RequiredArgsConstructor
public class AccesoController {
    private final AccesoService acceso;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/registro")
    public String registro(Model model) {
        model.addAttribute("cuenta", new AccesoFormulario());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("cuenta") AccesoFormulario cuenta, BindingResult errores, Model model) {
        if (errores.hasErrors()) return "registro";
        try {
            acceso.registrar(cuenta.getEmail(), cuenta.getPassword());
            return "redirect:/login?registrado";
        } catch (SilaboException e) {
            model.addAttribute("error", e.getMessage());
            return "registro";
        }
    }
}

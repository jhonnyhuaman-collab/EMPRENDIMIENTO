package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.service.CursoService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class CursoController {

    private final CursoService cursoService;
    private final Clock reloj;

    @GetMapping("/cursos")
    public String cursos(Model model) {
        model.addAttribute("cursos", cursoService.listarCursos());
        model.addAttribute("hoy", LocalDate.now(reloj));
        return "cursos";
    }

    @PostMapping("/cursos/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redireccion) {
        redireccion.addFlashAttribute("cursoEliminado", cursoService.eliminar(id, LocalDateTime.now(reloj)));
        return "redirect:/cursos";
    }
}

package pe.utec.planificador.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.dto.CategoriaAgenda;
import pe.utec.planificador.dto.formulario.PendienteFormulario;
import pe.utec.planificador.model.TipoEvaluacion;
import pe.utec.planificador.service.AgendaService;
import pe.utec.planificador.service.CursoService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Agenda: agregar tareas y evaluaciones que no estaban en el sílabo, editarlas y marcarlas como entregadas. */
@Controller
@RequestMapping("/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;
    private final CursoService cursoService;
    private final Clock reloj;

    @GetMapping
    public String agenda(@RequestParam(required = false) Long curso, @RequestParam(required = false) String ver, Model model) {
        PendienteFormulario nuevo = new PendienteFormulario();
        nuevo.setCursoId(curso);
        nuevo.setFecha(LocalDate.now(reloj).plusDays(1));
        model.addAttribute("ver", CategoriaAgenda.desdeParametro(ver).orElse(null));
        return mostrar(nuevo, null, model);
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("pendiente") PendienteFormulario pendiente, BindingResult errores,
                        Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            return mostrar(pendiente, null, model);
        }
        var creada = agendaService.crear(pendiente, LocalDateTime.now(reloj));
        redireccion.addFlashAttribute("mensajeAgenda", "«" + creada.getNombre() + "» se agregó a tu agenda y a tu plan de estudio.");
        return "redirect:/agenda";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return mostrar(agendaService.formularioDe(id), id, model);
    }

    @PostMapping("/{id}")
    public String actualizar(@PathVariable Long id, @Valid @ModelAttribute("pendiente") PendienteFormulario pendiente,
                             BindingResult errores, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            return mostrar(pendiente, id, model);
        }
        var actualizada = agendaService.actualizar(id, pendiente, LocalDateTime.now(reloj));
        redireccion.addFlashAttribute("mensajeAgenda", "«" + actualizada.getNombre() + "» se actualizó y tu plan se recalculó.");
        return "redirect:/agenda";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redireccion) {
        String nombre = agendaService.eliminar(id, LocalDateTime.now(reloj));
        redireccion.addFlashAttribute("mensajeAgenda", "«" + nombre + "» se eliminó de tu agenda.");
        return "redirect:/agenda";
    }

    @PostMapping("/{id}/completar")
    public String completar(@PathVariable Long id, @RequestParam(defaultValue = "/agenda") String volverA) {
        agendaService.alternarCompletada(id, LocalDateTime.now(reloj));
        return "redirect:" + RutasInternas.segura(volverA);
    }

    private String mostrar(PendienteFormulario pendiente, Long editandoId, Model model) {
        LocalDate hoy = LocalDate.now(reloj);
        model.addAttribute("pendiente", pendiente);
        model.addAttribute("editandoId", editandoId);
        model.addAttribute("cursos", cursoService.listarCursos());
        model.addAttribute("tipos", TipoEvaluacion.values());
        var secciones = agendaService.proximasPorCategoria(hoy);
        model.addAttribute("secciones", secciones);
        model.addAttribute("totalAgenda", secciones.stream().mapToInt(s -> s.getTotal()).sum());
        model.addAttribute("vencidas", agendaService.vencidasSinMarcar(hoy));
        model.addAttribute("hoy", hoy);
        return "agenda";
    }
}

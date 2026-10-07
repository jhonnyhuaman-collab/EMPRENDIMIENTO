package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pe.utec.planificador.dto.EventoCalendarioDto;
import pe.utec.planificador.service.CalendarioEventosService;
import pe.utec.planificador.service.CursoService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CalendarioController {

    private final CursoService cursoService;
    private final CalendarioEventosService calendarioEventosService;

    @GetMapping("/calendario")
    public String calendario(Model model) {
        model.addAttribute("cursos", cursoService.listarCursos());
        return "calendario";
    }

    /** Eventos en JSON que consume FullCalendar. */
    @GetMapping("/api/eventos")
    @ResponseBody
    public List<EventoCalendarioDto> eventos() {
        return calendarioEventosService.listarEventos();
    }
}

package pe.utec.planificador.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.dto.EventoCalendarioDto;
import pe.utec.planificador.dto.formulario.HorarioFormulario;
import pe.utec.planificador.model.FrecuenciaClase;
import pe.utec.planificador.service.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** "Mi horario": vista semanal por colores e importación desde foto o consolidado de matrícula. */
@Controller
@RequiredArgsConstructor
public class HorarioController {

    private final HorarioSemanalService horarioSemanal;
    private final LectorArchivoHorarioService lectorArchivo;
    private final SeleccionIaService seleccionIa;
    private final PreparacionHorarioService preparacion;
    private final RegistroHorarioService registro;
    private final PreparacionSilaboService preparacionSilabo;
    private final Clock reloj;

    @GetMapping("/horario")
    public String horario(Model model) {
        model.addAttribute("cursos", horarioSemanal.cursosConHorario());
        model.addAttribute("horasSemanales", horarioSemanal.horasSemanales());
        model.addAttribute("hayQuincenales", horarioSemanal.hayClasesQuincenales());
        model.addAttribute("semanaActual", horarioSemanal.semanaDe(LocalDate.now(reloj)));
        agregarEstadoIa(model);
        return "horario";
    }

    @GetMapping("/api/horario")
    @ResponseBody
    public List<EventoCalendarioDto> eventos(@RequestParam(defaultValue = "") String semana) {
        return horarioSemanal.eventos(semana);
    }

    /** Lee la foto o el PDF con IA y muestra la tabla editable. */
    @PostMapping("/horario/analizar")
    public String analizar(@RequestParam("archivo") MultipartFile archivo, Model model) {
        try {
            var leido = seleccionIa.extraerHorario(lectorArchivo.leer(archivo));
            return mostrarRevision(preparacion.desdeIa(leido, archivo.getOriginalFilename()), model);
        } catch (SilaboException e) {
            model.addAttribute("error", e.getMessage());
            return horario(model);
        }
    }

    @PostMapping("/horario/guardar")
    public String guardar(@Valid @ModelAttribute("horario") HorarioFormulario horario, BindingResult errores,
                          Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            return mostrarRevision(horario, model);
        }
        try {
            redireccion.addFlashAttribute("resultadoHorario", registro.guardar(horario, LocalDateTime.now(reloj)));
            return "redirect:/horario";
        } catch (SilaboException e) {
            model.addAttribute("error", e.getMessage());
            return mostrarRevision(horario, model);
        }
    }

    private String mostrarRevision(HorarioFormulario horario, Model model) {
        model.addAttribute("horario", horario);
        model.addAttribute("dias", preparacionSilabo.opcionesDias());
        model.addAttribute("frecuencias", FrecuenciaClase.values());
        return "revisar-horario";
    }

    private void agregarEstadoIa(Model model) {
        var activo = seleccionIa.activo();
        model.addAttribute("iaConfigurada", activo.isPresent());
        model.addAttribute("proveedorIa", activo.map(ExtractorSilaboIa::getNombre).orElse(""));
        model.addAttribute("modeloIa", activo.map(ExtractorSilaboIa::getModelo).orElse(""));
        model.addAttribute("respaldosIa", seleccionIa.respaldos());
    }
}

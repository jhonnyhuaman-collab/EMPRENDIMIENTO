package pe.utec.planificador.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.dto.ResultadoPlanDto;
import pe.utec.planificador.dto.formulario.SilaboFormulario;
import pe.utec.planificador.model.TipoEvaluacion;
import pe.utec.planificador.service.*;

import java.time.Clock;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/silabos")
@RequiredArgsConstructor
public class SilaboController {

    private final LectorPdfService lectorPdf;
    private final SeleccionIaService seleccionIa;
    private final PreparacionSilaboService preparacion;
    private final RegistroSilaboService registro;
    private final EstudianteService estudianteService;
    private final Clock reloj;

    @GetMapping("/subir")
    public String subir(Model model) {
        agregarEstadoIa(model);
        return "subir-silabo";
    }

    /** Lee el PDF, lo analiza con IA y muestra la tabla editable. */
    @PostMapping("/analizar")
    public String analizar(@RequestParam("archivo") MultipartFile archivo, Model model) {
        try {
            String texto = lectorPdf.extraerTexto(archivo);
            var extraido = seleccionIa.extraer(texto, estudianteService.obtenerActual().getFechaInicioCiclo());
            return mostrarRevision(preparacion.desdeIa(extraido, archivo.getOriginalFilename()), model);
        } catch (SilaboException e) {
            model.addAttribute("error", e.getMessage());
            agregarEstadoIa(model);
            return "subir-silabo";
        }
    }

    /** Registro manual, sin IA. */
    @GetMapping("/manual")
    public String manual(Model model) {
        return mostrarRevision(preparacion.vacio(), model);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("silabo") SilaboFormulario silabo, BindingResult errores,
                          Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            return mostrarRevision(silabo, model);
        }
        try {
            ResultadoPlanDto plan = registro.guardar(silabo, LocalDateTime.now(reloj));
            redireccion.addFlashAttribute("resultadoPlan", plan);
            redireccion.addFlashAttribute("cursoGuardado", silabo.getCodigo().toUpperCase());
            return "redirect:/cursos";
        } catch (SilaboException e) {
            model.addAttribute("error", e.getMessage());
            return mostrarRevision(silabo, model);
        }
    }

    private String mostrarRevision(SilaboFormulario silabo, Model model) {
        model.addAttribute("silabo", silabo);
        model.addAttribute("tipos", TipoEvaluacion.values());
        model.addAttribute("dias", preparacion.opcionesDias());
        String codigoExistente = silabo.getCodigoExistente() == null || silabo.getCodigoExistente().isBlank()
                ? silabo.getCodigo() : silabo.getCodigoExistente();
        model.addAttribute("cursoExiste", registro.existeCurso(codigoExistente));
        model.addAttribute("codigoExistente", codigoExistente);
        return "revisar-silabo";
    }

    private void agregarEstadoIa(Model model) {
        var activo = seleccionIa.activo();
        model.addAttribute("iaConfigurada", activo.isPresent());
        model.addAttribute("proveedorIa", activo.map(ExtractorSilaboIa::getNombre).orElse(""));
        model.addAttribute("modeloIa", activo.map(ExtractorSilaboIa::getModelo).orElse(""));
        model.addAttribute("respaldosIa", seleccionIa.respaldos());
    }
}

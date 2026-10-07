package pe.utec.planificador.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.utec.planificador.service.ExportadorIcsService;
import pe.utec.planificador.service.PlanificadorService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class PlanController {

    private static final MediaType CALENDARIO = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final PlanificadorService planificadorService;
    private final ExportadorIcsService exportadorIcs;
    private final Clock reloj;

    @PostMapping("/plan/generar")
    public String generar(@RequestParam(defaultValue = "/") String volverA, RedirectAttributes redireccion) {
        redireccion.addFlashAttribute("resultadoPlan", planificadorService.generarPlan(LocalDateTime.now(reloj)));
        return "redirect:" + RutasInternas.segura(volverA);
    }

    /** Descarga el plan como archivo .ics para importarlo en Google Calendar. */
    @GetMapping("/plan/exportar.ics")
    public ResponseEntity<byte[]> exportar() {
        String ics = exportadorIcs.generar(LocalDateTime.now(reloj), reloj.getZone());
        return ResponseEntity.ok()
                .contentType(CALENDARIO)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("plan-de-estudio.ics").build().toString())
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }
}

package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.utec.planificador.model.BloqueEstudio;
import pe.utec.planificador.model.Estudiante;
import pe.utec.planificador.model.Evaluacion;
import pe.utec.planificador.repository.BloqueEstudioRepository;
import pe.utec.planificador.repository.EvaluacionRepository;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Genera el plan en formato iCalendar (.ics, RFC 5545) para importarlo en Google Calendar.
 * Incluye todas las evaluaciones (eventos de día completo) y los bloques de estudio desde hoy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportadorIcsService {

    private static final String SALTO = "\r\n";
    private static final int MAXIMO_OCTETOS_POR_LINEA = 75;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FECHA_HORA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final EstudianteService estudianteService;
    private final EvaluacionRepository evaluacionRepository;
    private final BloqueEstudioRepository bloqueRepository;

    public String generar(LocalDateTime ahora, ZoneId zona) {
        Estudiante estudiante = estudianteService.obtenerActual();
        String marcaTiempo = enUtc(ahora, zona);
        StringBuilder ics = new StringBuilder();
        linea(ics, "BEGIN:VCALENDAR");
        linea(ics, "VERSION:2.0");
        linea(ics, "PRODID:-//UTEC Emprendimiento//Planificador Academico//ES");
        linea(ics, "CALSCALE:GREGORIAN");
        linea(ics, "METHOD:PUBLISH");
        linea(ics, "X-WR-CALNAME:Plan de estudio");
        linea(ics, "X-WR-TIMEZONE:" + zona.getId());

        for (Evaluacion e : evaluacionRepository.findByCursoEstudianteIdOrderByFechaAsc(estudiante.getId())) {
            agregarEvaluacion(ics, e, marcaTiempo);
        }
        bloqueRepository.findByEvaluacionCursoEstudianteIdOrderByFechaAscHoraInicioAsc(estudiante.getId()).stream()
                .filter(b -> !b.getFecha().isBefore(ahora.toLocalDate()) && !b.isReprogramado())
                .forEach(b -> agregarBloque(ics, b, marcaTiempo, zona));

        linea(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private void agregarEvaluacion(StringBuilder ics, Evaluacion e, String marcaTiempo) {
        String temas = e.getTemas().stream().map(t -> t.getNumero() + " " + t.getTitulo())
                .collect(Collectors.joining(", "));
        linea(ics, "BEGIN:VEVENT");
        linea(ics, "UID:evaluacion-" + e.getId() + "@planificador-academico");
        linea(ics, "DTSTAMP:" + marcaTiempo);
        linea(ics, "DTSTART;VALUE=DATE:" + e.getFecha().format(FECHA));
        linea(ics, "DTEND;VALUE=DATE:" + e.getFecha().plusDays(1).format(FECHA));
        linea(ics, "SUMMARY:" + escapar("📌 " + e.getCurso().getNombre() + " · " + e.getNombre()
                + " (" + porcentaje(e.getPesoEnNotaFinal()) + ")"));
        linea(ics, "DESCRIPTION:" + escapar(e.getTipo().getEtiqueta() + " de " + e.getCurso().getNombre()
                + ". Vale " + porcentaje(e.getPesoEnNotaFinal()) + " de tu nota final."
                + (temas.isEmpty() ? "" : "\nTemas: " + temas)));
        linea(ics, "TRANSP:TRANSPARENT");
        linea(ics, "END:VEVENT");
    }

    private void agregarBloque(StringBuilder ics, BloqueEstudio b, String marcaTiempo, ZoneId zona) {
        Evaluacion e = b.getEvaluacion();
        linea(ics, "BEGIN:VEVENT");
        linea(ics, "UID:bloque-" + b.getId() + "@planificador-academico");
        linea(ics, "DTSTAMP:" + marcaTiempo);
        linea(ics, "DTSTART:" + enUtc(LocalDateTime.of(b.getFecha(), b.getHoraInicio()), zona));
        linea(ics, "DTEND:" + enUtc(LocalDateTime.of(b.getFecha(), b.getHoraFin()), zona));
        linea(ics, "SUMMARY:" + escapar((b.isCompletado() ? "✓ " : "📖 ") + e.getCurso().getNombre() + " · " + b.getDescripcion()));
        linea(ics, "DESCRIPTION:" + escapar("Estudio para " + e.getNombre() + " de " + e.getCurso().getNombre()
                + " (" + porcentaje(e.getPesoEnNotaFinal()) + " de la nota)."));
        linea(ics, "BEGIN:VALARM");
        linea(ics, "ACTION:DISPLAY");
        linea(ics, "DESCRIPTION:" + escapar("En 10 minutos: " + b.getDescripcion()));
        linea(ics, "TRIGGER:-PT10M");
        linea(ics, "END:VALARM");
        linea(ics, "END:VEVENT");
    }

    // ---------- Formato iCalendar ----------

    private String enUtc(LocalDateTime fechaHora, ZoneId zona) {
        return fechaHora.atZone(zona).withZoneSameInstant(ZoneOffset.UTC).format(FECHA_HORA_UTC);
    }

    private String porcentaje(Double peso) {
        return String.format(Locale.US, "%.1f%%", peso == null ? 0 : peso);
    }

    /** Escapa los caracteres especiales del texto (RFC 5545 §3.3.11). */
    static String escapar(String texto) {
        return texto.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /** Agrega una línea plegada a 75 octetos como exige el estándar (las continuaciones empiezan con espacio). */
    static void linea(StringBuilder ics, String contenido) {
        int octetos = 0;
        int limite = MAXIMO_OCTETOS_POR_LINEA;
        for (int i = 0; i < contenido.length(); ) {
            int puntoDeCodigo = contenido.codePointAt(i);
            String caracter = new String(Character.toChars(puntoDeCodigo));
            int tamano = caracter.getBytes(StandardCharsets.UTF_8).length;
            if (octetos + tamano > limite) {
                ics.append(SALTO).append(' ');
                octetos = 0;
                limite = MAXIMO_OCTETOS_POR_LINEA - 1; // el espacio inicial cuenta
            }
            ics.append(caracter);
            octetos += tamano;
            i += Character.charCount(puntoDeCodigo);
        }
        ics.append(SALTO);
    }
}

package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.OpcionDia;
import pe.utec.planificador.dto.formulario.FilaEvaluacion;
import pe.utec.planificador.dto.formulario.FilaHorario;
import pe.utec.planificador.dto.formulario.FilaTema;
import pe.utec.planificador.dto.formulario.SilaboFormulario;
import pe.utec.planificador.dto.ia.EvaluacionExtraidaDto;
import pe.utec.planificador.dto.ia.HorarioExtraidoDto;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.HorarioClase;
import pe.utec.planificador.model.TipoEvaluacion;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;

/** Convierte lo que extrajo la IA en la tabla editable de revisión (aplicando las reglas de peso y horas). */
@Service
@RequiredArgsConstructor
public class PreparacionSilaboService {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");

    private final CalculadoraPesoService calculadoraPeso;
    private final EstimadorHorasService estimadorHoras;
    private final CursoService cursoService;
    private final DiaEvaluacionService diaEvaluacion;

    public SilaboFormulario desdeIa(SilaboExtraidoDto extraido, String nombreArchivo) {
        SilaboFormulario formulario = new SilaboFormulario();
        formulario.setCodigo(texto(extraido.codigo()).toUpperCase());
        formulario.setNombre(texto(extraido.nombre()));
        formulario.setCreditos(Math.max(0, extraido.creditos()));
        formulario.setDificultad(Math.max(1, Math.min(5, extraido.dificultadEstimada())));
        Optional<Curso> existente = buscarCursoExistente(formulario.getCodigo(), formulario.getNombre());
        existente.ifPresent(c -> formulario.setCodigoExistente(c.getCodigo()));
        // Si el curso ya vino del horario, conserva su color
        formulario.setColor(existente.map(Curso::getColor).orElseGet(this::colorDisponible));
        formulario.setNombreArchivo(nombreArchivo);
        formulario.setObservaciones(texto(extraido.observaciones()));

        lista(extraido.temas()).forEach(t ->
                formulario.getTemas().add(new FilaTema(t.unidad(), texto(t.numero()), texto(t.titulo()))));

        // El horario importado (foto o matrícula) manda sobre el que traiga el sílabo
        List<HorarioClase> sesiones;
        if (existente.isPresent() && !existente.get().getHorarios().isEmpty()) {
            sesiones = existente.get().getHorarios();
        } else {
            lista(extraido.horarios()).stream()
                    .map(this::filaHorario)
                    .filter(Objects::nonNull)
                    .forEach(formulario.getHorarios()::add);
            sesiones = formulario.getHorarios().stream()
                    .map(h -> HorarioClase.builder().diaSemana(h.getDia()).horaInicio(h.getHoraInicio()).horaFin(h.getHoraFin()).build())
                    .toList();
        }

        lista(extraido.evaluaciones()).forEach(e ->
                formulario.getEvaluaciones().add(filaEvaluacion(e, formulario.getDificultad(), sesiones)));
        return formulario;
    }

    /** Formulario vacío para registrar un curso a mano (sin IA). */
    public SilaboFormulario vacio() {
        SilaboFormulario formulario = new SilaboFormulario();
        formulario.setColor(colorDisponible());
        formulario.getEvaluaciones().add(new FilaEvaluacion("", TipoEvaluacion.EXAMEN, 8, DayOfWeek.MONDAY,
                20.0, estimadorHoras.estimarHoras(TipoEvaluacion.EXAMEN, 3, 20.0), "", ""));
        return formulario;
    }

    public List<OpcionDia> opcionesDias() {
        return Arrays.stream(DayOfWeek.values())
                .map(d -> {
                    String nombre = d.getDisplayName(TextStyle.FULL, ESPANOL);
                    return new OpcionDia(d, Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1));
                })
                .toList();
    }

    /** Primer color de la paleta que aún no usa ningún curso del estudiante. */
    public String colorDisponible() {
        Set<String> usados = new HashSet<>();
        cursoService.listarCursos().forEach(c -> usados.add(c.getColor()));
        return PaletaColores.siguiente(usados);
    }

    /** Busca el curso ya registrado: primero por código y, si no, por nombre (el horario a veces no trae código). */
    private Optional<Curso> buscarCursoExistente(String codigo, String nombre) {
        List<Curso> cursos = cursoService.listarCursos();
        if (!codigo.isEmpty()) {
            Optional<Curso> porCodigo = cursos.stream().filter(c -> c.getCodigo().equalsIgnoreCase(codigo)).findFirst();
            if (porCodigo.isPresent()) {
                return porCodigo;
            }
        }
        String buscado = normalizar(nombre);
        if (buscado.length() < 4) {
            return Optional.empty();
        }
        return cursos.stream()
                .filter(c -> {
                    String actual = normalizar(c.getNombre());
                    return actual.equals(buscado) || (actual.length() >= 6 && (actual.contains(buscado) || buscado.contains(actual)));
                })
                .findFirst();
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private FilaEvaluacion filaEvaluacion(EvaluacionExtraidaDto e, int dificultad, List<HorarioClase> sesiones) {
        TipoEvaluacion tipo = e.tipo() == null ? TipoEvaluacion.TAREA : e.tipo();
        double peso = calculadoraPeso.pesoReal(e.pesoComponente(), e.pesoDentroDelComponente());
        String detalle = e.pesoComponente() >= 100 ? ""
                : formatear(e.pesoComponente()) + "% × " + formatear(e.pesoDentroDelComponente()) + "%";
        int semana = Math.max(1, Math.min(20, e.semana()));
        DayOfWeek dia = diaEvaluacion.elegirDia(tipo, semana, sesiones);
        return new FilaEvaluacion(texto(e.nombre()), tipo, semana, dia, peso,
                estimadorHoras.estimarHoras(tipo, dificultad, peso),
                String.join(", ", lista(e.temas())), detalle);
    }

    private FilaHorario filaHorario(HorarioExtraidoDto h) {
        try {
            LocalTime inicio = LocalTime.parse(texto(h.horaInicio()));
            LocalTime fin = LocalTime.parse(texto(h.horaFin()));
            return h.dia() == null || !fin.isAfter(inicio) ? null : new FilaHorario(h.dia(), inicio, fin);
        } catch (DateTimeParseException ex) {
            return null; // horario ilegible: el estudiante puede agregarlo a mano
        }
    }

    private String formatear(double numero) {
        return numero == Math.rint(numero) ? String.valueOf((long) numero) : String.valueOf(numero);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }
}

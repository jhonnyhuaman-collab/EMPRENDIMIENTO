package pe.utec.planificador.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.utec.planificador.dto.formulario.FilaCursoHorario;
import pe.utec.planificador.dto.formulario.FilaSesion;
import pe.utec.planificador.dto.formulario.HorarioFormulario;
import pe.utec.planificador.dto.ia.CursoMatriculadoDto;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SesionClaseDto;
import pe.utec.planificador.model.Curso;
import pe.utec.planificador.model.FrecuenciaClase;

import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Convierte el horario que leyó la IA en la tabla editable: un color distinto por curso. */
@Service
@RequiredArgsConstructor
public class PreparacionHorarioService {

    private final CursoService cursoService;

    public HorarioFormulario desdeIa(HorarioMatriculaDto horario, String nombreArchivo) {
        Map<String, Curso> existentes = new HashMap<>();
        cursoService.listarCursos().forEach(c -> existentes.put(c.getCodigo().toUpperCase(Locale.ROOT), c));
        Set<String> coloresUsados = new HashSet<>();
        existentes.values().forEach(c -> coloresUsados.add(c.getColor()));
        Set<String> codigosUsados = new HashSet<>();

        HorarioFormulario formulario = new HorarioFormulario();
        formulario.setNombreArchivo(nombreArchivo);
        formulario.setObservaciones(texto(horario.observaciones()));

        for (CursoMatriculadoDto c : lista(horario.cursos())) {
            String codigo = texto(c.codigo()).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            if (codigo.isEmpty()) {
                codigo = codigoDesdeNombre(texto(c.nombre()), codigosUsados);
            }
            if (!codigosUsados.add(codigo)) {
                continue; // curso repetido: la IA ya debió juntar sus sesiones
            }
            Curso existente = existentes.get(codigo);
            String color;
            if (existente != null) {
                color = existente.getColor();
            } else {
                color = PaletaColores.siguiente(coloresUsados);
                coloresUsados.add(color);
            }
            String nombre = existente != null && texto(c.nombre()).isEmpty() ? existente.getNombre() : texto(c.nombre());
            formulario.getCursos().add(new FilaCursoHorario(codigo, nombre, Math.max(0, c.creditos()), color, existente != null));

            for (SesionClaseDto s : lista(c.sesiones())) {
                FilaSesion fila = filaSesion(codigo, s);
                if (fila != null) {
                    formulario.getSesiones().add(fila);
                }
            }
        }
        formulario.getSesiones().sort(Comparator.comparing(FilaSesion::getDia).thenComparing(FilaSesion::getHoraInicio));
        return formulario;
    }

    private FilaSesion filaSesion(String codigo, SesionClaseDto s) {
        try {
            LocalTime inicio = LocalTime.parse(texto(s.horaInicio()));
            LocalTime fin = LocalTime.parse(texto(s.horaFin()));
            if (s.dia() == null || !fin.isAfter(inicio)) {
                return null;
            }
            FrecuenciaClase frecuencia = s.frecuencia() == null ? FrecuenciaClase.SEMANAL : s.frecuencia();
            return new FilaSesion(codigo, s.dia(), inicio, fin, texto(s.detalle()), frecuencia);
        } catch (DateTimeParseException e) {
            return null; // hora ilegible: el estudiante puede agregarla a mano
        }
    }

    /** Si el documento no trae código, se arma con las iniciales del nombre (ej. "Cálculo Vectorial" → CV). */
    String codigoDesdeNombre(String nombre, Set<String> usados) {
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder iniciales = new StringBuilder();
        for (String palabra : sinTildes.split("[^A-Za-z0-9]+")) {
            if (palabra.length() > 2 || palabra.chars().allMatch(Character::isDigit)) {
                iniciales.append(Character.toUpperCase(palabra.charAt(0)));
            }
        }
        String base = iniciales.isEmpty() ? "CURSO" : iniciales.substring(0, Math.min(6, iniciales.length()));
        String codigo = base;
        for (int n = 2; usados.contains(codigo); n++) {
            codigo = base + n;
        }
        return codigo;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }
}

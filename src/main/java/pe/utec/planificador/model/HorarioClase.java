package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioClase {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private DayOfWeek diaSemana;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    /** Información extra opcional, ej. "Teoría · Aula A-505". */
    private String detalle;

    /** Semanal o quincenal (Semana A / Semana B). Nulo en datos antiguos = semanal. */
    @Enumerated(EnumType.STRING)
    private FrecuenciaClase frecuencia;

    @ManyToOne(fetch = FetchType.LAZY)
    private Curso curso;

    public FrecuenciaClase getFrecuencia() {
        return frecuencia == null ? FrecuenciaClase.SEMANAL : frecuencia;
    }

    /** ¿Hay esta clase en la semana N del ciclo? */
    public boolean aplicaEnSemana(int semana) {
        return getFrecuencia().aplicaEnSemana(semana);
    }

    /** Nombre del día en español, ej. "Lunes". */
    public String getDiaTexto() {
        String dia = diaSemana.getDisplayName(TextStyle.FULL, ESPANOL);
        return Character.toUpperCase(dia.charAt(0)) + dia.substring(1);
    }
}

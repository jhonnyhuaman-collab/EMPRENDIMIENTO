package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    @Column(unique = true)
    private String email;
    private String passwordHash;

    /** Lunes de la semana 1 del ciclo. */
    private LocalDate fechaInicioCiclo;

    private Double horasEstudioPorDia;
    private LocalTime horaDormir;

    /** Días en que el plan puede poner estudio, ej. "MONDAY,TUESDAY". Nulo = lunes a viernes. */
    private String diasEstudio;

    /** Máximo de horas por día sumando clases y estudio (para no saturar los días con muchas clases). */
    private Double cargaMaximaDiaria;

    /** Máximo de horas de estudio por curso en una semana (para no sobrecargar un solo curso). */
    private Double horasMaximasCursoSemana;

    @OneToMany(mappedBy = "estudiante", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("codigo")
    @Builder.Default
    private List<Curso> cursos = new ArrayList<>();

    public static final Set<DayOfWeek> LUNES_A_VIERNES = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    public static final double CARGA_MAXIMA_POR_DEFECTO = 9.0;
    public static final double HORAS_CURSO_SEMANA_POR_DEFECTO = 4.0;

    public Set<DayOfWeek> getDiasEstudioSet() {
        if (diasEstudio == null || diasEstudio.isBlank()) {
            return LUNES_A_VIERNES;
        }
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(diasEstudio.split(",")).map(String::strip).filter(d -> !d.isEmpty())
                .forEach(d -> dias.add(DayOfWeek.valueOf(d)));
        return dias.isEmpty() ? LUNES_A_VIERNES : dias;
    }

    public void setDiasEstudioSet(Collection<DayOfWeek> dias) {
        this.diasEstudio = String.join(",", dias.stream().sorted().map(DayOfWeek::name).toList());
    }

    public double getCargaMaximaDiariaEfectiva() {
        return cargaMaximaDiaria == null ? CARGA_MAXIMA_POR_DEFECTO : cargaMaximaDiaria;
    }

    public double getHorasMaximasCursoSemanaEfectivas() {
        return horasMaximasCursoSemana == null ? HORAS_CURSO_SEMANA_POR_DEFECTO : horasMaximasCursoSemana;
    }

    public void agregarCurso(Curso curso) {
        curso.setEstudiante(this);
        cursos.add(curso);
    }
}

package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String nombre;
    private Integer creditos;

    /** De 1 (fácil) a 5 (muy difícil). */
    private Integer dificultad;

    /** Color hexadecimal para el calendario, ej. "#1971C2". */
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    private Estudiante estudiante;

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    @Builder.Default
    private List<Tema> temas = new ArrayList<>();

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha")
    @Builder.Default
    private List<Evaluacion> evaluaciones = new ArrayList<>();

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("diaSemana, horaInicio")
    @Builder.Default
    private List<HorarioClase> horarios = new ArrayList<>();

    public void agregarTema(Tema tema) {
        tema.setCurso(this);
        temas.add(tema);
    }

    public void agregarEvaluacion(Evaluacion evaluacion) {
        evaluacion.setCurso(this);
        evaluaciones.add(evaluacion);
    }

    public void agregarHorario(HorarioClase horario) {
        horario.setCurso(this);
        horarios.add(horario);
    }

    /** Suma de pesos de todas las evaluaciones (debería dar 100%). */
    public double getPesoTotal() {
        return evaluaciones.stream()
                .mapToDouble(e -> e.getPesoEnNotaFinal() == null ? 0 : e.getPesoEnNotaFinal())
                .sum();
    }
}

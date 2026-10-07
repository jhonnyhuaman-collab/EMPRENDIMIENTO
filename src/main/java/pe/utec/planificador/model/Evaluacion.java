package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoEvaluacion tipo;

    /** Semana del ciclo según el sílabo (1-16). */
    private Integer semana;

    private LocalDate fecha;

    /** Peso real en la nota final, en %. */
    private Double pesoEnNotaFinal;

    private Double horasEstimadas;

    /** Nota obtenida (opcional, 0-20). */
    private Double nota;

    /** De dónde vino: del sílabo o agregada a mano en la agenda. Nulo en datos antiguos = sílabo. */
    @Enumerated(EnumType.STRING)
    private OrigenEvaluacion origen;

    /** Hora límite de entrega (opcional), ej. 23:59. */
    private LocalTime horaEntrega;

    /** Ya se entregó o rindió: el plan deja de programarle estudio. */
    @Column(columnDefinition = "boolean default false not null")
    private boolean completada;

    @Column(length = 500)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    private Curso curso;

    @ManyToMany
    @JoinTable(name = "evaluacion_tema",
            joinColumns = @JoinColumn(name = "evaluacion_id"),
            inverseJoinColumns = @JoinColumn(name = "tema_id"))
    @OrderBy("numero")
    @Builder.Default
    private List<Tema> temas = new ArrayList<>();

    @OneToMany(mappedBy = "evaluacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha, horaInicio")
    @Builder.Default
    private List<BloqueEstudio> bloques = new ArrayList<>();

    public OrigenEvaluacion getOrigen() {
        return origen == null ? OrigenEvaluacion.SILABO : origen;
    }

    public boolean esDeAgenda() {
        return getOrigen() == OrigenEvaluacion.AGENDA;
    }

    public void agregarBloque(BloqueEstudio bloque) {
        bloque.setEvaluacion(this);
        bloques.add(bloque);
    }
}

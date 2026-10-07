package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String descripcion;
    private boolean completado;

    /** Bloque de un día pasado que no se hizo: su tiempo se volvió a repartir en el plan. */
    @Column(columnDefinition = "boolean default false not null")
    private boolean reprogramado;

    @ManyToOne(fetch = FetchType.LAZY)
    private Evaluacion evaluacion;

    public double getDuracionHoras() {
        return Duration.between(horaInicio, horaFin).toMinutes() / 60.0;
    }
}

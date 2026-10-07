package pe.utec.planificador.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de unidad del sílabo (1, 2, 3...). */
    private Integer unidad;

    /** Numeración del sílabo, ej. "6.2". */
    private String numero;

    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Curso curso;
}

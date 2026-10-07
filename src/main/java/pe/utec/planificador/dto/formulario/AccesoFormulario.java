package pe.utec.planificador.dto.formulario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccesoFormulario {
    @NotBlank(message = "Escribe tu correo")
    @Email(message = "Escribe un correo valido")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Escribe tu contrasena")
    @Size(min = 8, max = 64, message = "Usa entre 8 y 64 caracteres")
    private String password;
}

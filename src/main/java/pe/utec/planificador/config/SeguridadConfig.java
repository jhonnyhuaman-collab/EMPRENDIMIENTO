package pe.utec.planificador.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pe.utec.planificador.repository.EstudianteRepository;

@Configuration
public class SeguridadConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(EstudianteRepository estudiantes) {
        return correo -> estudiantes.findByEmailIgnoreCase(correo.strip())
                .filter(e -> e.getPasswordHash() != null)
                .map(e -> User.withUsername(e.getEmail()).password(e.getPasswordHash()).roles("ESTUDIANTE").build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas"));
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/manifest.webmanifest", "/sw.js", "/offline.html").permitAll()
                        .requestMatchers("/login", "/registro", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                        .defaultSuccessUrl("/", true).failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?salida"))
                .build();
    }
}

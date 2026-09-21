package cl.duoc.pedidos360.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad: el backend actúa como OAuth2 Resource Server
 * y valida los JWT emitidos por Microsoft Entra ID.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // habilita @PreAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API stateless con Bearer token: CSRF no aplica
            .csrf(AbstractHttpConfigurer::disable)
            // El frontend (localhost:4200) es otro origen
            .cors(Customizer.withDefaults())
            // Sin sesiones HTTP: cada petición se autentica solo con su JWT
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS: el navegador no envía token en OPTIONS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Único endpoint público (health check del API Gateway)
                .requestMatchers("/api/health").permitAll()
                // Todo lo demás bajo /api requiere JWT válido
                .requestMatchers("/api/**").authenticated()
                // Cualquier otra ruta también se protege por defecto
                .anyRequest().authenticated()
            )
            // Valida el JWT (firma, issuer, expiración, audience) según application.properties
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /** CORS para permitir que el frontend Angular llame a la API con el header Authorization. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${pedidos360.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

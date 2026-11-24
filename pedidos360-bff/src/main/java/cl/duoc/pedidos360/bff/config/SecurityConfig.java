package cl.duoc.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Seguridad del BFF (WebFlux). Valida el JWT y hace un primer filtro por rol según la ruta, antes de gastar una
 * llamada al microservicio. Cada microservicio vuelve a validar el token y sus reglas finas (defensa en profundidad).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain filtros(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(rutas -> rutas
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/api/health", "/actuator/health").permitAll()
                        .pathMatchers("/api/report/**").hasRole("ADMIN")
                        .pathMatchers("/api/kafka-admin/**", "/api/admin/**", "/actuator/gateway/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/audit", "/api/audit/**").hasAnyRole("ADMIN", "AUDITOR")
                        .pathMatchers("/api/audit", "/api/audit/**").denyAll()   // auditoría es solo lectura
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(new RolesJwtConverter()))))
                .build();
    }

    /** CORS en un solo lugar: el navegador habla con el BFF (a través del API Gateway), no con cada microservicio. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.stream(origenes.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Igual que en los microservicios: scp → SCOPE_*, roles (App Roles) → ROLE_*; sin rol = Cliente. */
    static class RolesJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

        private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            Collection<GrantedAuthority> permisos = new ArrayList<>(scopes.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            (roles == null || roles.isEmpty() ? List.of("Cliente") : roles).forEach(rol -> permisos.add(
                    new SimpleGrantedAuthority("ROLE_" + Normalizer.normalize(rol.trim(), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").toUpperCase())));
            return new JwtAuthenticationToken(jwt, permisos, jwt.getClaimAsString("preferred_username"));
        }
    }
}

package cl.duoc.pedidos360.report.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Traduce el JWT de Entra ID a permisos de Spring Security:
 * <ul>
 *   <li>claim {@code scp} ("OT.Create") → {@code SCOPE_OT.Create} (lo que la app puede hacer en nombre del usuario)</li>
 *   <li>claim {@code roles} (App Roles: Admin, Operador, Cliente, Auditor) → {@code ROLE_ADMIN}, {@code ROLE_OPERADOR}...
 *       (lo que el USUARIO tiene permitido)</li>
 * </ul>
 * Un usuario sin rol asignado en Entra ID se trata como Cliente (el caso de un cliente que se registra solo).
 */
public class RolesJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROL_POR_DEFECTO = "Cliente";

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> permisos = new ArrayList<>(scopes.convert(jwt));
        rolesDe(jwt).forEach(rol -> permisos.add(new SimpleGrantedAuthority("ROLE_" + normalizar(rol))));
        return new JwtAuthenticationToken(jwt, permisos, jwt.getClaimAsString("preferred_username"));
    }

    /** Roles del token tal como vienen de Entra ID; [Cliente] si no trae ninguno. */
    public static List<String> rolesDe(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null || roles.isEmpty() ? List.of(ROL_POR_DEFECTO) : roles;
    }

    /** "Operador" → "OPERADOR" (sin tildes, en mayúsculas), para usarlo en hasRole('OPERADOR'). */
    static String normalizar(String rol) {
        return Normalizer.normalize(rol.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
    }
}

package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.Actor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/** Arma el {@link Actor} de la auditoría a partir del JWT (quién) y de la petición HTTP (desde dónde). */
@Component
public class ActorResolver {

    public Actor desde(Jwt jwt, HttpServletRequest request) {
        String id = jwt.getClaimAsString("oid") != null ? jwt.getClaimAsString("oid") : jwt.getSubject();
        String nombre = jwt.getClaimAsString("name") != null ? jwt.getClaimAsString("name")
                : jwt.getClaimAsString("preferred_username");
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new Actor(id, nombre, roles, ipDeOrigen(request), request.getHeader("User-Agent"));
    }

    /**
     * Detrás del API Gateway la IP directa es la del Gateway; la del usuario viene en X-Forwarded-For
     * ("cliente, proxy1, proxy2"): se toma la primera.
     */
    static String ipDeOrigen(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

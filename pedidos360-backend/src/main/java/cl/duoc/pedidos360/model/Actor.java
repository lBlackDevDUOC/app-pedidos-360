package cl.duoc.pedidos360.model;

import java.util.List;

/**
 * Quién hizo una acción y desde dónde. Sale del JWT (id, nombre, roles) y de la petición HTTP (IP, navegador).
 * Lo necesita la auditoría: "quién / qué / cuándo / desde dónde".
 *
 * @param id        oid del usuario en Entra ID
 * @param roles     app roles del token (Admin, Operador, Cliente, Auditor); vacío si no tiene
 * @param ip        IP de origen (X-Forwarded-For cuando viene a través del API Gateway)
 */
public record Actor(String id, String nombre, List<String> roles, String ip, String userAgent) {

    public Actor {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** Para procesos internos (ej. un consumidor de mensajes) donde no hay un usuario detrás. */
    public static Actor sistema(String componente) {
        return new Actor(componente, componente, List.of("SISTEMA"), null, null);
    }
}

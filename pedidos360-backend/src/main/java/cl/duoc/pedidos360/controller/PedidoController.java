package cl.duoc.pedidos360.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Endpoints de pedidos (datos mock). Requieren un JWT válido de Entra ID. */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    /** Modelo simple de pedido. */
    public record Pedido(int id, String producto, double monto, String estado) {}

    /** Lista los pedidos e indica qué usuario hizo la petición. */
    @GetMapping
    public Map<String, Object> listar(@AuthenticationPrincipal Jwt jwt) {
        List<Pedido> pedidos = List.of(
                new Pedido(1, "Notebook Lenovo", 549990.0, "ENTREGADO"),
                new Pedido(2, "Mouse inalámbrico", 15990.0, "EN_CAMINO"),
                new Pedido(3, "Monitor 27 pulgadas", 189990.0, "PENDIENTE"));

        return Map.of("usuario", usuarioDe(jwt), "pedidos", pedidos);
    }

    /**
     * Crea un pedido. Exige el scope "OT.Create" en el token
     * (Spring lo mapea a la authority SCOPE_OT.Create desde la claim "scp" de Entra ID).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<Map<String, Object>> crear(@RequestBody(required = false) Map<String, Object> pedido,
                                                     @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> respuesta = Map.of(
                "mensaje", "Pedido creado correctamente",
                "creadoPor", usuarioDe(jwt),
                "pedido", pedido != null ? pedido : Map.of());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /** Entra ID entrega "preferred_username" (correo); si no viene, se usa "sub". */
    private String usuarioDe(Jwt jwt) {
        String usuario = jwt.getClaimAsString("preferred_username");
        return usuario != null ? usuario : jwt.getSubject();
    }
}

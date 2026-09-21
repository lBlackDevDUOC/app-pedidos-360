package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Endpoints de pedidos respaldados por la base de datos. Requieren un JWT válido de Entra ID. */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoRepository pedidoRepository;

    public PedidoController(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /** Lista todos los pedidos guardados en la base de datos. */
    @GetMapping
    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    /** Obtiene un pedido por id; responde 404 si no existe. */
    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    /**
     * Crea un pedido. Exige el scope "OT.Create" en el token
     * (Spring lo mapea a la authority SCOPE_OT.Create desde la claim "scp" de Entra ID).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<Pedido> crear(@RequestBody Pedido pedido, @AuthenticationPrincipal Jwt jwt) {
        if (pedido.getTotal() == null || pedido.getTotal() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El total debe ser mayor a 0");
        }

        // El id lo genera la base de datos: se ignora cualquier valor enviado por el cliente
        pedido.setId(null);
        // Si no se indica cliente, se usa el usuario autenticado
        if (pedido.getCliente() == null || pedido.getCliente().isBlank()) {
            pedido.setCliente(usuarioDe(jwt));
        }
        if (pedido.getEstado() == null || pedido.getEstado().isBlank()) {
            pedido.setEstado("PENDIENTE");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoRepository.save(pedido));
    }

    /** Entra ID entrega "preferred_username" (correo); si no viene, se usa "sub". */
    private String usuarioDe(Jwt jwt) {
        String usuario = jwt.getClaimAsString("preferred_username");
        return usuario != null ? usuario : jwt.getSubject();
    }
}

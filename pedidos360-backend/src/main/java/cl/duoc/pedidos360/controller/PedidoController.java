package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.repository.PedidoRepository;
import cl.duoc.pedidos360.service.ClienteService;
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

/**
 * Pedidos del usuario autenticado. El cliente siempre sale del token JWT de Entra ID:
 * cada usuario crea y ve únicamente sus propios pedidos.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    /** Cuerpo del POST: el cliente no se envía, se toma del token. */
    public record PedidoRequest(Double total, String estado) {}

    private final PedidoRepository pedidoRepository;
    private final ClienteService clienteService;

    public PedidoController(PedidoRepository pedidoRepository, ClienteService clienteService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteService = clienteService;
    }

    /** Lista los pedidos del usuario logueado. */
    @GetMapping
    public List<Pedido> listar(@AuthenticationPrincipal Jwt jwt) {
        Cliente cliente = clienteService.obtenerOCrear(jwt);
        return pedidoRepository.findByClienteOidOrderByFechaCreacionDesc(cliente.getOid());
    }

    /** Obtiene un pedido propio por id; si no existe o es de otro usuario responde 404. */
    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Cliente cliente = clienteService.obtenerOCrear(jwt);
        return pedidoRepository.findById(id)
                .filter(p -> p.getCliente().getId().equals(cliente.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    /**
     * Crea un pedido para el usuario logueado. Exige el scope "OT.Create" en el token
     * (Spring lo mapea a la authority SCOPE_OT.Create desde la claim "scp" de Entra ID).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_OT.Create')")
    public ResponseEntity<Pedido> crear(@RequestBody PedidoRequest req, @AuthenticationPrincipal Jwt jwt) {
        if (req.total() == null || req.total() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El total debe ser mayor a 0");
        }

        Cliente cliente = clienteService.obtenerOCrear(jwt);
        String estado = (req.estado() == null || req.estado().isBlank()) ? "PENDIENTE" : req.estado();
        Pedido guardado = pedidoRepository.save(new Pedido(cliente, req.total(), estado));
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }
}

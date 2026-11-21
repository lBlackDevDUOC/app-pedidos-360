package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import cl.duoc.pedidos360.service.ClienteService;
import cl.duoc.pedidos360.service.PedidoService;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedidos del usuario autenticado. El cliente siempre sale del token JWT de Entra ID:
 * cada usuario crea y ve únicamente sus propios pedidos.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    /**
     * Cuerpo del POST. Lo normal es mandar items (productos del catálogo): el total lo calcula el backend con los
     * precios del catálogo. "total" sin items queda solo por compatibilidad con las entregas anteriores.
     * El cliente se toma del token y el estado inicial siempre es CREADO.
     */
    public record PedidoRequest(@Positive(message = "El total debe ser mayor a 0") Double total,
                                @Size(max = 50, message = "Máximo 50 líneas por pedido") List<@Valid ItemRequest> items) {

        @JsonIgnore
        @AssertTrue(message = "Indica los productos (items) o, en su defecto, el total")
        public boolean isItemsOTotal() {
            return (items != null && !items.isEmpty()) || total != null;
        }
    }

    public record ItemRequest(@NotNull(message = "productoId es obligatorio") Long productoId,
                              @NotNull @Min(value = 1, message = "La cantidad mínima es 1")
                              @Max(value = 100, message = "La cantidad máxima es 100") Integer cantidad) {}

    /** Cuerpo del PUT /{id}/estado. Un valor que no sea un EstadoPedido válido responde 400. */
    public record CambioEstadoRequest(@NotNull(message = "El estado es obligatorio") EstadoPedido estado) {}

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ActorResolver actorResolver;

    public PedidoController(PedidoService pedidoService, ClienteService clienteService, ActorResolver actorResolver) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.actorResolver = actorResolver;
    }

    /**
     * Lista pedidos. Cliente: los suyos. Operador/Admin: los de todos (resuelto en el servicio según el rol).
     * Filtros opcionales: ?estado=ACEPTADO&desde=...&hasta=... (ISO-8601).
     */
    @GetMapping
    public List<Pedido> listar(
            @RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return pedidoService.listar(clienteService.obtenerOCrear(jwt), actorResolver.desde(jwt, request),
                estado, desde, hasta);
    }

    /** Obtiene un pedido por id; un Cliente que pide uno ajeno recibe 404. */
    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return pedidoService.obtener(id, clienteService.obtenerOCrear(jwt), actorResolver.desde(jwt, request));
    }

    /**
     * Crea un pedido para el usuario logueado. Exige el scope "OT.Create" (claim scp) y ser Cliente u Operador
     * (App Roles de Entra ID, claim roles). Un Admin administra, no compra: recibe 403.
     * El comprobante se genera de forma asíncrona: el pedido se devuelve sin él y aparece al recargar.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_OT.Create') and hasAnyRole('CLIENTE', 'OPERADOR')")
    public ResponseEntity<Pedido> crear(@Valid @RequestBody PedidoRequest req, @AuthenticationPrincipal Jwt jwt,
                                        HttpServletRequest request) {
        Cliente cliente = clienteService.obtenerOCrear(jwt);
        var actor = actorResolver.desde(jwt, request);
        Pedido creado = req.items() != null && !req.items().isEmpty()
                ? pedidoService.crear(cliente, req.items().stream()
                        .map(i -> new PedidoService.LineaPedido(i.productoId(), i.cantidad())).toList(), actor)
                : pedidoService.crear(cliente, req.total(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * Avanza el pedido en su ciclo de vida. Transición no permitida → 409. Un Cliente solo puede cancelar los
     * suyos (el servicio responde 403 si intenta otro estado).
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_OT.Create')")
    public Pedido cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest req,
                                @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return pedidoService.cambiarEstado(id, req.estado(), clienteService.obtenerOCrear(jwt),
                actorResolver.desde(jwt, request));
    }
}

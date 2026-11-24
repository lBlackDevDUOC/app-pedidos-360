package cl.duoc.pedidos360.catalog.controller;

import cl.duoc.pedidos360.catalog.model.Producto;
import cl.duoc.pedidos360.catalog.model.StockInsuficienteException;
import cl.duoc.pedidos360.catalog.service.CatalogoService;
import cl.duoc.pedidos360.catalog.service.CatalogoService.LineaStock;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * API del catálogo. Lectura: cualquier usuario autenticado (el cliente elige productos para su pedido).
 * Crear/editar: Admin. Mover stock: lo llama ms-pedidos con el token del usuario que acepta o cancela el pedido.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogoController {

    public record ProductoRequest(
            @NotBlank(message = "es obligatorio") @Pattern(regexp = "^[A-Z0-9-]{3,40}$",
                    message = "solo mayúsculas, números y '-' (3 a 40)") String sku,
            @NotBlank(message = "es obligatorio") @Size(max = 120) String nombre,
            @Size(max = 500) String descripcion,
            @Size(max = 60) String categoria,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor a 0") Double precio,
            @NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo") Integer stock) {}

    /** PUT parcial: los campos nulos no se cambian. */
    public record ActualizarProductoRequest(
            @Size(max = 120) String nombre, @Size(max = 500) String descripcion, @Size(max = 60) String categoria,
            @Positive(message = "debe ser mayor a 0") Double precio,
            @PositiveOrZero(message = "no puede ser negativo") Integer stock, Boolean activo) {}

    public record Linea(@NotNull Long productoId, @NotNull @Min(1) @Max(1000) Integer cantidad) {}

    public record MovimientoRequest(@NotNull(message = "es obligatorio") Long pedidoId,
                                    @NotEmpty(message = "debe tener al menos una línea") List<@Valid Linea> items) {}

    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(summary = "Lista productos activos (incluirInactivos=true para Admin/Operador)")
    @GetMapping("/products")
    public List<Producto> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return catalogo.listar(incluirInactivos);
    }

    @GetMapping("/products/{id}")
    public Producto obtener(@PathVariable Long id) {
        return catalogo.obtener(id);
    }

    @Operation(summary = "Crea un producto (Admin)")
    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogo.crear(r.sku(), r.nombre(), r.descripcion(), r.categoria(), r.precio(), r.stock()));
    }

    @Operation(summary = "Actualiza precio, stock u otros datos (Admin)")
    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarProductoRequest r) {
        return catalogo.actualizar(id, r.nombre(), r.descripcion(), r.categoria(), r.precio(), r.stock(), r.activo());
    }

    @Operation(summary = "Descuenta stock de un pedido aceptado (Operador/Admin). 409 si no alcanza")
    @PostMapping("/stock/descontar")
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    public ResponseEntity<Void> descontar(@Valid @RequestBody MovimientoRequest r) {
        catalogo.descontar(r.pedidoId(), r.items().stream().map(l -> new LineaStock(l.productoId(), l.cantidad())).toList());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Repone el stock descontado de un pedido cancelado")
    @PostMapping("/stock/reponer/{pedidoId}")
    public ResponseEntity<Void> reponer(@PathVariable Long pedidoId) {
        catalogo.reponer(pedidoId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- errores
    @ExceptionHandler(StockInsuficienteException.class)
    public ProblemDetail stockInsuficiente(StockInsuficienteException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Stock insuficiente");
        p.setProperty("productoId", e.getProductoId());
        p.setProperty("disponible", e.getDisponible());
        p.setProperty("solicitado", e.getSolicitado());
        return p;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail noEncontrado(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class})
    public ProblemDetail conflicto(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e instanceof OptimisticLockingFailureException
                ? "Otro usuario modificó el producto al mismo tiempo, vuelve a intentarlo" : e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalido(MethodArgumentNotValidException e) {
        Map<String, String> errores = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.merge(f.getField(), f.getDefaultMessage(),
                (a, b) -> a + "; " + b));
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La petición tiene campos inválidos");
        p.setProperty("errores", errores);
        return p;
    }
}

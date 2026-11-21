package cl.duoc.pedidos360.catalog.service;

import cl.duoc.pedidos360.catalog.model.MovimientoStock;
import cl.duoc.pedidos360.catalog.model.Producto;
import cl.duoc.pedidos360.catalog.repository.MovimientoStockRepository;
import cl.duoc.pedidos360.catalog.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reglas del catálogo: CRUD de productos y movimientos de stock por pedido (idempotentes y todo-o-nada). */
@Service
public class CatalogoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoService.class);

    private final ProductoRepository productos;
    private final MovimientoStockRepository movimientos;

    public CatalogoService(ProductoRepository productos, MovimientoStockRepository movimientos) {
        this.productos = productos;
        this.movimientos = movimientos;
    }

    public record LineaStock(Long productoId, int cantidad) {}

    @Transactional(readOnly = true)
    public List<Producto> listar(boolean incluirInactivos) {
        return incluirInactivos ? productos.findAllByOrderByCategoriaAscNombreAsc()
                : productos.findByActivoTrueOrderByCategoriaAscNombreAsc();
    }

    @Transactional(readOnly = true)
    public Producto obtener(Long id) {
        return productos.findById(id).orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + id));
    }

    @Transactional
    public Producto crear(String sku, String nombre, String descripcion, String categoria, Double precio, Integer stock) {
        if (productos.existsBySku(sku)) {
            throw new IllegalStateException("Ya existe un producto con SKU " + sku);
        }
        Producto creado = productos.save(new Producto(sku, nombre, descripcion, categoria, precio, stock));
        log.info("[CATALOGO] Producto creado {} {} precio={} stock={}", creado.getId(), nombre, precio, stock);
        return creado;
    }

    @Transactional
    public Producto actualizar(Long id, String nombre, String descripcion, String categoria, Double precio,
                               Integer stock, Boolean activo) {
        Producto producto = obtener(id);
        producto.actualizar(nombre, descripcion, categoria, precio, stock, activo);
        log.info("[CATALOGO] Producto {} actualizado precio={} stock={} activo={}", id, producto.getPrecio(),
                producto.getStock(), producto.isActivo());
        return productos.save(producto);
    }

    /**
     * Descuenta el stock de todas las líneas de un pedido. Todo o nada: si una línea no alcanza, la transacción
     * se revierte y ningún producto cambia. Idempotente: si el pedido ya tenía su descuento, no hace nada.
     */
    @Transactional
    public void descontar(Long pedidoId, List<LineaStock> lineas) {
        if (!movimientos.findByPedidoIdAndTipo(pedidoId, MovimientoStock.Tipo.DESCUENTO).isEmpty()) {
            log.info("[CATALOGO] Pedido {} ya tenía su stock descontado, se ignora (reintento)", pedidoId);
            return;
        }
        Map<Long, Integer> porProducto = lineas.stream()
                .collect(Collectors.toMap(LineaStock::productoId, LineaStock::cantidad, Integer::sum));
        Map<Long, Producto> bloqueados = productos.bloquearPorIds(porProducto.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        porProducto.forEach((productoId, cantidad) -> {
            Producto producto = bloqueados.get(productoId);
            if (producto == null || !producto.isActivo()) {
                throw new NoSuchElementException("Producto no disponible: " + productoId);
            }
            producto.descontar(cantidad);
            movimientos.save(new MovimientoStock(pedidoId, productoId, cantidad, MovimientoStock.Tipo.DESCUENTO));
        });
        log.info("[CATALOGO] Stock descontado para pedido {}: {}", pedidoId, porProducto);
    }

    /** Devuelve al stock lo que se descontó para el pedido (al cancelarlo). Idempotente. */
    @Transactional
    public void reponer(Long pedidoId) {
        if (!movimientos.findByPedidoIdAndTipo(pedidoId, MovimientoStock.Tipo.REPOSICION).isEmpty()) {
            return;
        }
        List<MovimientoStock> descuentos = movimientos.findByPedidoIdAndTipo(pedidoId, MovimientoStock.Tipo.DESCUENTO);
        Map<Long, Producto> bloqueados = productos.bloquearPorIds(
                descuentos.stream().map(MovimientoStock::getProductoId).toList()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        for (MovimientoStock d : descuentos) {
            bloqueados.get(d.getProductoId()).reponer(d.getCantidad());
            movimientos.save(new MovimientoStock(pedidoId, d.getProductoId(), d.getCantidad(),
                    MovimientoStock.Tipo.REPOSICION));
        }
        log.info("[CATALOGO] Stock repuesto para pedido {} ({} líneas)", pedidoId, descuentos.size());
    }
}

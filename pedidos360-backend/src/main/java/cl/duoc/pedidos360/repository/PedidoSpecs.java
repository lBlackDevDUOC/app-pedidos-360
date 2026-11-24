package cl.duoc.pedidos360.repository;

import cl.duoc.pedidos360.model.EstadoPedido;
import cl.duoc.pedidos360.model.Pedido;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Filtros de GET /api/pedidos?estado=&desde=&hasta=. Cada filtro es opcional: si el parámetro viene nulo
 * devuelve null y Spring Data simplemente no lo agrega al WHERE.
 */
public final class PedidoSpecs {

    private PedidoSpecs() {}

    public static Specification<Pedido> delCliente(String oid) {
        return oid == null ? null : (root, query, cb) -> cb.equal(root.get("cliente").get("oid"), oid);
    }

    public static Specification<Pedido> conEstado(EstadoPedido estado) {
        return estado == null ? null : (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    public static Specification<Pedido> creadoDesde(LocalDateTime desde) {
        return desde == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaCreacion"), desde);
    }

    public static Specification<Pedido> creadoHasta(LocalDateTime hasta) {
        return hasta == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaCreacion"), hasta);
    }
}

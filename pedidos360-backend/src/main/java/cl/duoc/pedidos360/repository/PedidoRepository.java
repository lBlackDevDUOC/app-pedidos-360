package cl.duoc.pedidos360.repository;

import cl.duoc.pedidos360.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** JpaSpecificationExecutor permite armar el WHERE con filtros opcionales (ver {@link PedidoSpecs}). */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {
}

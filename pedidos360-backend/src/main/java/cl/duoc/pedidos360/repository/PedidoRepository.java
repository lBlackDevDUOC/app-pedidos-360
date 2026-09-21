package cl.duoc.pedidos360.repository;

import cl.duoc.pedidos360.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Pedidos de un cliente (por su oid de Entra ID), los más recientes primero. */
    List<Pedido> findByClienteOidOrderByFechaCreacionDesc(String oid);
}

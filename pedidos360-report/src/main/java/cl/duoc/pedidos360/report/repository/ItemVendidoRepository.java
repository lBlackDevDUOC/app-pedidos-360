package cl.duoc.pedidos360.report.repository;

import cl.duoc.pedidos360.report.model.ItemVendido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItemVendidoRepository extends JpaRepository<ItemVendido, Long> {

    boolean existsByPedidoId(Long pedidoId);

    List<ItemVendido> findByPedidoIdIn(Collection<Long> pedidoIds);
}

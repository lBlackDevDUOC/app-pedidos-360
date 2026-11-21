package cl.duoc.pedidos360.catalog.repository;

import cl.duoc.pedidos360.catalog.model.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    List<MovimientoStock> findByPedidoIdAndTipo(Long pedidoId, MovimientoStock.Tipo tipo);
}

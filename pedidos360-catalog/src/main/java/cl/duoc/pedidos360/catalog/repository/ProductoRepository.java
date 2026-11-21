package cl.duoc.pedidos360.catalog.repository;

import cl.duoc.pedidos360.catalog.model.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByActivoTrueOrderByCategoriaAscNombreAsc();

    List<Producto> findAllByOrderByCategoriaAscNombreAsc();

    boolean existsBySku(String sku);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE) de los productos de un pedido mientras se descuenta stock:
     * dos operadores aceptando pedidos del mismo producto a la vez no pueden vender la última unidad dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id in :ids order by p.id")
    List<Producto> bloquearPorIds(@Param("ids") Collection<Long> ids);
}

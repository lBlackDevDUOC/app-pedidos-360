package cl.duoc.pedidos360.report.repository;

import cl.duoc.pedidos360.report.model.PedidoProyeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Consultas JPQL estándar (sin SQL propio de PostgreSQL): funcionan igual sobre Oracle u otra BD. */
public interface PedidoProyeccionRepository extends JpaRepository<PedidoProyeccion, Long> {

    List<PedidoProyeccion> findByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);

    List<PedidoProyeccion> findByFechaEntregaBetween(LocalDateTime desde, LocalDateTime hasta);

    /** Cantidad de pedidos por estado, solo de los estados indicados. Cada fila: [estado, cantidad]. */
    @Query("select p.estado, count(p) from PedidoProyeccion p where p.estado not in :excluidos group by p.estado")
    List<Object[]> contarPorEstadoExcepto(@Param("excluidos") Collection<String> excluidos);

    @Query("select max(p.ultimoEventoEn) from PedidoProyeccion p")
    Optional<Instant> ultimoEvento();
}

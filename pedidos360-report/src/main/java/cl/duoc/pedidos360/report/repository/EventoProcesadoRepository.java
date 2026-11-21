package cl.duoc.pedidos360.report.repository;

import cl.duoc.pedidos360.report.model.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}

package cl.duoc.pedidos360.audit.repository;

import cl.duoc.pedidos360.audit.model.RegistroAuditoria;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, String>,
        JpaSpecificationExecutor<RegistroAuditoria> {

    /** Timeline completa de una entidad (ej. un pedido), en orden cronológico. */
    List<RegistroAuditoria> findByEntidadAndEntidadIdOrderByOcurridoEnAsc(String entidad, String entidadId);

    /** Filtros opcionales de GET /api/audit: un parámetro nulo no se agrega al WHERE. */
    final class Filtros {
        private Filtros() {}

        public static Specification<RegistroAuditoria> usuario(String texto) {
            if (texto == null || texto.isBlank()) return null;
            String patron = "%" + texto.toLowerCase() + "%";
            return (r, q, cb) -> cb.or(cb.like(cb.lower(r.get("actorId")), patron),
                    cb.like(cb.lower(r.get("actorNombre")), patron));
        }

        public static Specification<RegistroAuditoria> accion(String accion) {
            return accion == null || accion.isBlank() ? null : (r, q, cb) -> cb.equal(r.get("accion"), accion);
        }

        public static Specification<RegistroAuditoria> entidadId(String id) {
            return id == null || id.isBlank() ? null : (r, q, cb) -> cb.equal(r.get("entidadId"), id);
        }

        public static Specification<RegistroAuditoria> desde(Instant desde) {
            return desde == null ? null : (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("ocurridoEn"), desde);
        }

        public static Specification<RegistroAuditoria> hasta(Instant hasta) {
            return hasta == null ? null : (r, q, cb) -> cb.lessThanOrEqualTo(r.get("ocurridoEn"), hasta);
        }
    }
}

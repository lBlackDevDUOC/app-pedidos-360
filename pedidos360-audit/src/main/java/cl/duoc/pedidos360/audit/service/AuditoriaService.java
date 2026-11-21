package cl.duoc.pedidos360.audit.service;

import cl.duoc.pedidos360.audit.model.RegistroAuditoria;
import cl.duoc.pedidos360.audit.repository.RegistroAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static cl.duoc.pedidos360.audit.repository.RegistroAuditoriaRepository.Filtros;

/** Guarda y consulta la línea de tiempo. No conoce Kafka: recibe registros ya armados por el consumidor. */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final int MAX_POR_PAGINA = 200;

    private final RegistroAuditoriaRepository repositorio;

    public AuditoriaService(RegistroAuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** @return false si el evento ya estaba registrado (entrega duplicada de Kafka) */
    @Transactional
    public boolean registrar(RegistroAuditoria registro) {
        if (repositorio.existsById(registro.getEventoId())) {
            log.info("[DUPLICADO] eventoId={} ya registrado, se ignora", registro.getEventoId());
            return false;
        }
        repositorio.save(registro);
        return true;
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> buscar(String usuario, String accion, String entidadId, Instant desde, Instant hasta,
                                          int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        Specification<RegistroAuditoria> filtro = Specification.allOf(
                Filtros.usuario(usuario), Filtros.accion(accion), Filtros.entidadId(entidadId),
                Filtros.desde(desde), Filtros.hasta(hasta));
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, MAX_POR_PAGINA),
                Sort.by(Sort.Direction.DESC, "ocurridoEn"));
        return repositorio.findAll(filtro, pageRequest);
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> timelinePedido(Long pedidoId) {
        return repositorio.findByEntidadAndEntidadIdOrderByOcurridoEnAsc("PEDIDO", String.valueOf(pedidoId));
    }
}

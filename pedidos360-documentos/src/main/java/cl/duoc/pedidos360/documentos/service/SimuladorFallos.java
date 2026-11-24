package cl.duoc.pedidos360.documentos.service;

import cl.duoc.pedidos360.documentos.config.SimulacionProperties;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simula fallos del servicio externo de folios para mostrar en vivo cómo responde el consumidor:
 * reintento exitoso, reintentos agotados (DLQ) y error no recuperable (DLQ directo).
 */
@Component
public class SimuladorFallos {

    private final SimulacionProperties props;
    /** Pedidos que ya fallaron una vez (para el caso "falla una vez y se recupera"). */
    private final Set<Long> yaFallaron = ConcurrentHashMap.newKeySet();

    public SimuladorFallos(SimulacionProperties props) {
        this.props = props;
    }

    public void aplicar(Long pedidoId, double total) {
        if (!props.habilitada()) {
            return;
        }
        if (Double.compare(total, props.totalFallaSiempre()) == 0) {
            throw new ServicioNoDisponibleException("[SIMULACION] El servicio de folios no responde");
        }
        if (Double.compare(total, props.totalFallaUnaVez()) == 0 && yaFallaron.add(pedidoId)) {
            throw new ServicioNoDisponibleException("[SIMULACION] Timeout transitorio del servicio de folios");
        }
        if (Double.compare(total, props.totalDatosInvalidos()) == 0) {
            throw new DatosPedidoInvalidosException("[SIMULACION] Datos tributarios del cliente inválidos");
        }
    }
}

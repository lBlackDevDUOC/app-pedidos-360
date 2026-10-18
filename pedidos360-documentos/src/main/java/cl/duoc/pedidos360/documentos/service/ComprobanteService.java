package cl.duoc.pedidos360.documentos.service;

import cl.duoc.pedidos360.documentos.model.Comprobante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Emite el comprobante de un pedido. No conoce RabbitMQ: recibe datos simples y devuelve el comprobante.
 * El número se deriva del id del pedido, así un mensaje duplicado genera exactamente el mismo comprobante
 * (operación idempotente).
 */
@Service
public class ComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);
    private static final double FACTOR_IVA = 1.19;

    private final SimuladorFallos simuladorFallos;

    public ComprobanteService(SimuladorFallos simuladorFallos) {
        this.simuladorFallos = simuladorFallos;
    }

    public Comprobante emitir(Long pedidoId, String clienteNombre, String clienteEmail, Double total) {
        if (total == null || total <= 0) {
            throw new DatosPedidoInvalidosException("El pedido " + pedidoId + " tiene un total inválido: " + total);
        }
        simuladorFallos.aplicar(pedidoId, total);

        double neto = Math.round(total / FACTOR_IVA);
        double iva = total - neto;
        Comprobante comprobante = new Comprobante(
                "BOL-%08d".formatted(pedidoId), pedidoId, clienteNombre, clienteEmail,
                total, neto, iva, LocalDateTime.now());

        log.info("""
                        Comprobante emitido
                          N°:      {}
                          Pedido:  {}
                          Cliente: {} <{}>
                          Neto: {}  IVA: {}  Total: {}""",
                comprobante.numero(), pedidoId, clienteNombre, clienteEmail,
                comprobante.neto(), comprobante.iva(), comprobante.total());
        return comprobante;
    }
}

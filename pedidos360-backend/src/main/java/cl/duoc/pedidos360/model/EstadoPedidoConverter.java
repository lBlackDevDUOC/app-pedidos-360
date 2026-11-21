package cl.duoc.pedidos360.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Guarda el estado como texto (columna varchar, igual que antes) y traduce los valores que quedaron en la BD
 * de la versión anterior, cuando el estado era un String libre ("PENDIENTE"). Así no hace falta migrar datos.
 */
@Converter(autoApply = true)
public class EstadoPedidoConverter implements AttributeConverter<EstadoPedido, String> {

    @Override
    public String convertToDatabaseColumn(EstadoPedido estado) {
        return estado == null ? null : estado.name();
    }

    @Override
    public EstadoPedido convertToEntityAttribute(String valor) {
        if (valor == null || valor.isBlank()) {
            return EstadoPedido.CREADO;
        }
        try {
            return EstadoPedido.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Valores heredados ("PENDIENTE" u otro texto libre): el pedido todavía no fue aceptado
            return EstadoPedido.CREADO;
        }
    }
}

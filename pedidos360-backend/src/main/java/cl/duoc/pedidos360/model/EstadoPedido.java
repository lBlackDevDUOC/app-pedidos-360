package cl.duoc.pedidos360.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un pedido. Cada estado declara a qué estados puede pasar, así la regla de negocio
 * ("no se puede despachar sin aceptar") vive en un solo lugar y no repartida en if's por el código.
 *
 * <pre>
 * CREADO ──▶ ACEPTADO ──▶ EN_PREPARACION ──▶ DESPACHADO ──▶ ENTREGADO
 *    │           │               │
 *    └───────────┴───────────────┴──▶ CANCELADO
 * </pre>
 * Un pedido despachado ya salió del local: no se puede cancelar. ENTREGADO y CANCELADO son finales.
 */
public enum EstadoPedido {

    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    /** Estados a los que se puede pasar desde este. */
    public Set<EstadoPedido> siguientes() {
        return switch (this) {
            case CREADO -> EnumSet.of(ACEPTADO, CANCELADO);
            case ACEPTADO -> EnumSet.of(EN_PREPARACION, CANCELADO);
            case EN_PREPARACION -> EnumSet.of(DESPACHADO, CANCELADO);
            case DESPACHADO -> EnumSet.of(ENTREGADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoPedido.class);
        };
    }

    public boolean puedeCambiarA(EstadoPedido destino) {
        return siguientes().contains(destino);
    }

    public boolean esFinal() {
        return siguientes().isEmpty();
    }
}

package cl.duoc.pedidos360.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/admin/exchanges. */
public record CrearExchangeRequest(
        @Schema(example = "demo.eventos")
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
        String nombre,

        @Schema(example = "TOPIC")
        @NotNull(message = "es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        TipoExchange tipo,

        @Schema(description = "Por defecto true")
        Boolean durable) {

    public boolean esDurable() {
        return durable == null || durable;
    }
}

package cl.duoc.pedidos360.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/admin/bindings: enlaza una cola a un exchange con una routing key. */
public record CrearBindingRequest(
        @Schema(example = "demo.eventos")
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
        String exchange,

        @Schema(example = "demo.reportes.q")
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ReglasNombres.PATRON_NOMBRE, message = ReglasNombres.MENSAJE_NOMBRE)
        String cola,

        @Schema(example = "reporte.*", description = "Vacía o ausente para exchanges fanout")
        @Pattern(regexp = ReglasNombres.PATRON_ROUTING_KEY, message = ReglasNombres.MENSAJE_ROUTING_KEY)
        String routingKey) {

    public String routingKeyONula() {
        return routingKey == null ? "" : routingKey;
    }
}

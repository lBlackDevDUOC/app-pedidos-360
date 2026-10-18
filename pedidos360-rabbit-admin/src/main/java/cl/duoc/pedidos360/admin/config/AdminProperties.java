package cl.duoc.pedidos360.admin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Configuración del microservicio administrador (bloque {@code app.admin} de application.yml). */
@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        @Valid @NotNull Management management,
        @NotNull List<String> prefijosProtegidos,
        @Valid @NotNull Alertas alertas) {

    /** Acceso a la API HTTP del plugin rabbitmq_management. */
    public record Management(@NotBlank String url, @NotBlank String vhost,
                             @NotBlank String usuario, @NotBlank String clave) {}

    /** Las colas cuyo nombre termina en sufijoDlq generan alerta al llegar a umbralMensajes. */
    public record Alertas(@Positive long intervaloMs, @NotBlank String sufijoDlq, @Positive int umbralMensajes) {}

    public boolean esProtegido(String nombre) {
        return prefijosProtegidos.stream().anyMatch(nombre::startsWith);
    }
}

package cl.duoc.pedidos360.admin.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI en /swagger-ui.html con botón "Authorize" para pegar el token de Microsoft. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Pedidos360 - RabbitMQ Admin API", version = "1.0",
                description = "Crear, listar y eliminar colas, exchanges y bindings; purgar colas y ver alertas de DLQ."),
        security = @SecurityRequirement(name = "entraId"))
@SecurityScheme(name = "entraId", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}

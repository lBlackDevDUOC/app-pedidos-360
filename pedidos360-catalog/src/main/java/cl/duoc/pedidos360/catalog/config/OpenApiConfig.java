package cl.duoc.pedidos360.catalog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI en /swagger-ui.html con botón "Authorize" para pegar el token de Microsoft. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Pedidos360 - Catalog API", version = "1.0",
                description = "Productos, precios y stock. El stock se descuenta cuando un Operador acepta un pedido."),
        security = @SecurityRequirement(name = "entraId"))
@SecurityScheme(name = "entraId", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}

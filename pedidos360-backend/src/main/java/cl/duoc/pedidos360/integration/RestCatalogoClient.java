package cl.duoc.pedidos360.integration;

import cl.duoc.pedidos360.model.ItemPedido;
import cl.duoc.pedidos360.service.Catalogo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Cliente HTTP de ms-catalog. <b>Token relay</b>: reenvía el mismo JWT del usuario que hizo la petición, así
 * ms-catalog aplica sus propias reglas de rol (solo un Operador/Admin puede descontar stock). No hay
 * "usuario técnico" con permisos totales entre microservicios.
 */
@Component
public class RestCatalogoClient implements Catalogo {

    private static final Logger log = LoggerFactory.getLogger(RestCatalogoClient.class);

    private final RestClient http;

    public RestCatalogoClient(RestClient.Builder builder, @Value("${app.catalogo.url}") String url) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(Duration.ofSeconds(3));
        tiempos.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.baseUrl(url).requestFactory(tiempos)
                .requestInterceptor((request, body, execution) -> {
                    String token = tokenActual();
                    if (token != null) {
                        request.getHeaders().setBearerAuth(token);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public List<ProductoCatalogo> productos(Collection<Long> ids) {
        Set<Long> buscados = Set.copyOf(ids);
        List<ProductoCatalogo> todos = llamar(() -> http.get().uri("/api/catalog/products")
                .retrieve().body(new ParameterizedTypeReference<List<ProductoCatalogo>>() {}));
        return todos == null ? List.of() : todos.stream().filter(p -> buscados.contains(p.id())).toList();
    }

    @Override
    public void descontarStock(Long pedidoId, List<ItemPedido> items) {
        var lineas = items.stream()
                .map(i -> Map.of("productoId", i.getProductoId(), "cantidad", i.getCantidad())).toList();
        try {
            llamar(() -> http.post().uri("/api/catalog/stock/descontar")
                    .body(Map.of("pedidoId", pedidoId, "items", lineas)).retrieve().toBodilessEntity());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new StockInsuficienteException(detalle(e, "Stock insuficiente"));
            }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ProductoNoDisponibleException(detalle(e, "Producto no disponible"));
            }
            throw e;
        }
    }

    @Override
    public void reponerStock(Long pedidoId) {
        llamar(() -> http.post().uri("/api/catalog/stock/reponer/{id}", pedidoId).retrieve().toBodilessEntity());
    }

    private <T> T llamar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            log.error("[CATALOGO-CAIDO] {}", e.getMessage());
            throw new CatalogoNoDisponibleException("ms-catalog no responde, intenta nuevamente", e);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new CatalogoNoDisponibleException("ms-catalog respondió " + e.getStatusCode(), e);
            }
            throw e;
        }
    }

    private static String detalle(RestClientResponseException e, String porDefecto) {
        var problema = e.getResponseBodyAs(Map.class);
        return problema != null && problema.get("detail") != null ? problema.get("detail").toString() : porDefecto;
    }

    private static String tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getTokenValue() : null;
    }
}

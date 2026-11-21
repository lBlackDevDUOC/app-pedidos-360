package cl.duoc.pedidos360.bff;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * El BFF enruta a un "microservicio" falso (servidor HTTP del JDK) y se verifica la seguridad por rol y que cada
 * request llega a la ruta correcta. (El header Origin no se prueba aquí: mockJwt arma peticiones sin host y Spring
 * las considera CORS malformado; con un navegador real funciona.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class BffRutasTest {

    private static final AtomicReference<String> ultimaRuta = new AtomicReference<>();
    private static final HttpServer falso = levantarFalso();

    private static HttpServer levantarFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", intercambio -> {
                ultimaRuta.set(intercambio.getRequestURI().getPath());
                byte[] cuerpo = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        String url = "http://localhost:" + falso.getAddress().getPort();
        for (String destino : new String[]{"PEDIDOS_URL", "CATALOG_URL", "REPORT_URL", "AUDIT_URL", "KAFKA_ADMIN_URL"}) {
            registro.add(destino, () -> url);
        }
    }

    @AfterAll
    static void apagar() {
        falso.stop(0);
    }

    @Autowired
    private WebTestClient web;

    @Test
    void sinTokenResponde401YLaSaludEsPublica() {
        web.get().uri("/api/pedidos").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/health").exchange().expectStatus().isOk();
    }

    @Test
    void enrutaPedidosYCatalogoConToken() {
        web.mutateWith(mockJwt()).get().uri("/api/pedidos?estado=CREADO").exchange().expectStatus().isOk();
        assertThat(ultimaRuta.get()).isEqualTo("/api/pedidos");

        web.mutateWith(mockJwt()).get().uri("/api/catalog/products").exchange().expectStatus().isOk();
        assertThat(ultimaRuta.get()).isEqualTo("/api/catalog/products");
    }

    @Test
    void reportesSoloParaAdmin() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .get().uri("/api/report/kpis").exchange().expectStatus().isForbidden();
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get().uri("/api/report/kpis").exchange().expectStatus().isOk();
        assertThat(ultimaRuta.get()).isEqualTo("/api/report/kpis");
    }

    @Test
    void auditoriaEsSoloLectura() {
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                .get().uri("/api/audit?usuario=ana").exchange().expectStatus().isOk();
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .delete().uri("/api/audit/x").exchange().expectStatus().isForbidden();
    }
}

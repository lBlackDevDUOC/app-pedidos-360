package cl.duoc.pedidos360.admin.controller;

import cl.duoc.pedidos360.admin.config.SecurityConfig;
import cl.duoc.pedidos360.admin.dto.BindingResponse;
import cl.duoc.pedidos360.admin.dto.ColaResponse;
import cl.duoc.pedidos360.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360.admin.dto.ExchangeResponse;
import cl.duoc.pedidos360.admin.dto.PurgaResponse;
import cl.duoc.pedidos360.admin.service.ConfiguracionInvalidaException;
import cl.duoc.pedidos360.admin.service.RabbitAdminService;
import cl.duoc.pedidos360.admin.service.RecursoNoEncontradoException;
import cl.duoc.pedidos360.admin.service.RecursoProtegidoException;
import cl.duoc.pedidos360.admin.service.RecursoYaExisteException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rúbrica: endpoints REST bien definidos (verbos y códigos HTTP) y validación de entrada con mensajes claros. */
@WebMvcTest(controllers = {ColaController.class, ExchangeController.class, BindingController.class})
@Import(SecurityConfig.class)
class AdminApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitAdminService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final ColaResponse COLA = new ColaResponse("demo.q", true, 0, 0, 0, 0, false, Map.of());

    @Nested
    class Seguridad {

        @Test
        void sinToken_401() throws Exception {
            mvc.perform(get("/api/admin/queues")).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
        }
    }

    @Nested
    class Colas {

        @Test
        void listar_200() throws Exception {
            when(service.listarColas()).thenReturn(List.of(COLA));

            mvc.perform(get("/api/admin/queues").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nombre").value("demo.q"));
        }

        @Test
        void crear_201ConLocation() throws Exception {
            when(service.crearCola(any())).thenReturn(COLA);

            mvc.perform(json(post("/api/admin/queues"), """
                            {"nombre":"demo.q","ttlMs":60000,"maxMensajes":100}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/admin/queues/demo.q")))
                    .andExpect(jsonPath("$.nombre").value("demo.q"));
            verify(service).crearCola(new CrearColaRequest("demo.q", null, 60000, 100, null, null));
        }

        @Test
        void crear_nombreVacio_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), "{\"nombre\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Datos inválidos"))
                    .andExpect(jsonPath("$.errores.nombre").exists());
            verifyNoInteractions(service);
        }

        @Test
        void crear_sinNombre_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre").value("es obligatorio"));
        }

        @Test
        void crear_nombreConEspacios_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), "{\"nombre\":\"mi cola\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre", containsString("sin espacios")));
        }

        @Test
        void crear_prefijoReservadoAmq_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), "{\"nombre\":\"amq.mia\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre", containsString("amq.")));
        }

        @Test
        void crear_ttlYLargoInvalidos_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), """
                            {"nombre":"demo.q","ttlMs":-5,"maxMensajes":0}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.ttlMs").value("debe ser mayor a 0"))
                    .andExpect(jsonPath("$.errores.maxMensajes").value("debe ser mayor a 0"));
        }

        @Test
        void crear_routingKeyDlxSinExchangeDlx_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), """
                            {"nombre":"demo.q","deadLetterRoutingKey":"x"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.deadLetterConsistente",
                            containsString("requiere indicar deadLetterExchange")));
        }

        @Test
        void crear_jsonMalFormado_400() throws Exception {
            mvc.perform(json(post("/api/admin/queues"), "{nombre:"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("JSON inválido"));
        }

        @Test
        void crear_yaExiste_409() throws Exception {
            when(service.crearCola(any())).thenThrow(new RecursoYaExisteException("La cola 'demo.q' ya existe"));

            mvc.perform(json(post("/api/admin/queues"), "{\"nombre\":\"demo.q\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("La cola 'demo.q' ya existe"));
        }

        @Test
        void crear_dlxInexistente_422() throws Exception {
            when(service.crearCola(any())).thenThrow(new ConfiguracionInvalidaException("El exchange DLX 'x' no existe"));

            mvc.perform(json(post("/api/admin/queues"), """
                            {"nombre":"demo.q","deadLetterExchange":"x"}"""))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void eliminar_204() throws Exception {
            mvc.perform(delete("/api/admin/queues/demo.q").with(jwt())).andExpect(status().isNoContent());
            verify(service).eliminarCola("demo.q");
        }

        @Test
        void eliminar_inexistente_404() throws Exception {
            doThrow(new RecursoNoEncontradoException("La cola 'nada.q' no existe")).when(service).eliminarCola("nada.q");

            mvc.perform(delete("/api/admin/queues/nada.q").with(jwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("La cola 'nada.q' no existe"));
        }

        @Test
        void eliminar_colaDelSistema_409() throws Exception {
            doThrow(new RecursoProtegidoException("protegida")).when(service).eliminarCola("pedidos.documento-generado.q");

            mvc.perform(delete("/api/admin/queues/pedidos.documento-generado.q").with(jwt()))
                    .andExpect(status().isConflict());
        }

        @Test
        void eliminar_nombreInvalidoEnRuta_400() throws Exception {
            mvc.perform(delete("/api/admin/queues/amq.gen-123").with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre").exists());
            verifyNoInteractions(service);
        }

        @Test
        void purgar_200() throws Exception {
            when(service.purgarCola("demo.dlq")).thenReturn(new PurgaResponse("demo.dlq", 4));

            mvc.perform(delete("/api/admin/queues/demo.dlq/messages").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensajesEliminados").value(4));
        }
    }

    @Nested
    class Exchanges {

        @Test
        void crear_201() throws Exception {
            when(service.crearExchange(any())).thenReturn(new ExchangeResponse("demo.ex", "topic", true));

            mvc.perform(json(post("/api/admin/exchanges"), "{\"nombre\":\"demo.ex\",\"tipo\":\"topic\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/admin/exchanges/demo.ex")));
        }

        @Test
        void crear_tipoInexistente_400ConValoresPermitidos() throws Exception {
            mvc.perform(json(post("/api/admin/exchanges"), "{\"nombre\":\"demo.ex\",\"tipo\":\"BROADCAST\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.tipo", containsString("DIRECT, TOPIC, FANOUT, HEADERS")));
            verifyNoInteractions(service);
        }

        @Test
        void crear_sinTipo_400() throws Exception {
            mvc.perform(json(post("/api/admin/exchanges"), "{\"nombre\":\"demo.ex\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.tipo").exists());
        }

        @Test
        void eliminar_204() throws Exception {
            mvc.perform(delete("/api/admin/exchanges/demo.ex").with(jwt())).andExpect(status().isNoContent());
            verify(service).eliminarExchange("demo.ex");
        }
    }

    @Nested
    class Bindings {

        @Test
        void crear_201() throws Exception {
            when(service.crearBinding(any())).thenReturn(new BindingResponse("demo.ex", "demo.q", "reporte.*"));

            mvc.perform(json(post("/api/admin/bindings"), """
                            {"exchange":"demo.ex","cola":"demo.q","routingKey":"reporte.*"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.routingKey").value("reporte.*"));
        }

        @Test
        void crear_routingKeyInvalida_400() throws Exception {
            mvc.perform(json(post("/api/admin/bindings"), """
                            {"exchange":"demo.ex","cola":"demo.q","routingKey":"con espacio"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.routingKey").exists());
        }

        @Test
        void eliminar_sinParametroObligatorio_400() throws Exception {
            mvc.perform(delete("/api/admin/bindings").param("exchange", "demo.ex").with(jwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.cola").value("es obligatorio"));
        }

        @Test
        void eliminar_204() throws Exception {
            mvc.perform(delete("/api/admin/bindings")
                            .param("exchange", "demo.ex").param("cola", "demo.q").param("routingKey", "reporte.*")
                            .with(jwt()))
                    .andExpect(status().isNoContent());
            verify(service).eliminarBinding("demo.ex", "demo.q", "reporte.*");
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

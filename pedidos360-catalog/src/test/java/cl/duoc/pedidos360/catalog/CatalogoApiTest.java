package cl.duoc.pedidos360.catalog;

import cl.duoc.pedidos360.catalog.config.SecurityConfig;
import cl.duoc.pedidos360.catalog.controller.CatalogoController;
import cl.duoc.pedidos360.catalog.service.CatalogoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas por rol del catálogo. */
@WebMvcTest(CatalogoController.class)
@Import(SecurityConfig.class)
class CatalogoApiTest {

    private static final String PRODUCTO = """
            {"sku":"PAN-DEMO","nombre":"Pan demo","precio":1000,"stock":5}""";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CatalogoService service;

    @Test
    void clienteConsultaElCatalogo() throws Exception {
        when(service.listar(false)).thenReturn(List.of());
        mvc.perform(get("/api/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());
    }

    @Test
    void soloAdminCreaProductos() throws Exception {
        mvc.perform(post("/api/catalog/products").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/products").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }

    @Test
    void clienteNoPuedeDescontarStock() throws Exception {
        mvc.perform(post("/api/catalog/stock/descontar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\":1,\"items\":[{\"productoId\":1,\"cantidad\":1}]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaElProducto() throws Exception {
        mvc.perform(post("/api/catalog/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"x\",\"precio\":-1}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precio").value("debe ser mayor a 0"))
                .andExpect(jsonPath("$.errores.nombre").value("es obligatorio"));
    }
}

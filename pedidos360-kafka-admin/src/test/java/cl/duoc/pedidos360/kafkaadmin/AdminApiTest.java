package cl.duoc.pedidos360.kafkaadmin;

import cl.duoc.pedidos360.kafkaadmin.controller.TopicoController;
import cl.duoc.pedidos360.kafkaadmin.service.AdminExceptions.OperacionNoPermitida;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Capa HTTP: seguridad (JWT) y validación de entrada, sin tocar Kafka (el servicio es un mock). */
@WebMvcTest(TopicoController.class)
@Import({cl.duoc.pedidos360.kafkaadmin.config.SecurityConfig.class,
        cl.duoc.pedidos360.kafkaadmin.error.ErroresApiHandler.class})
class AdminApiTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private KafkaAdminService service;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/kafka-admin/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    void crearConDatosInvalidosResponde400ConDetallePorCampo() throws Exception {
        mvc.perform(post("/api/kafka-admin/topics").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"__malo\",\"particiones\":0,\"factorReplicacion\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.particiones").value("mínimo 1"))
                .andExpect(jsonPath("$.errores.factorReplicacion").exists());
    }

    @Test
    void eliminarTopicoDelSistemaResponde409() throws Exception {
        doThrow(new OperacionNoPermitida("es del sistema")).when(service).eliminarTopico("orders.events");

        mvc.perform(delete("/api/kafka-admin/topics/orders.events").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"));
    }
}

package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.repository.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClienteServiceTest {

    private final ClienteRepository repository = mock(ClienteRepository.class);
    private final ClienteService service = new ClienteService(repository);

    private static Jwt token() {
        return Jwt.withTokenValue("t").header("alg", "none")
                .claim("oid", "oid-1").claim("name", "Ana").claim("preferred_username", "ana@duocuc.cl")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void primerLoginEnParalelo_laPeticionQuePierdeLeeElClienteYaCreado() {
        Cliente creadoPorLaOtraPeticion = new Cliente("oid-1", "Ana", "ana@duocuc.cl");
        when(repository.findByOid("oid-1"))
                .thenReturn(Optional.empty())                         // no existía al consultar
                .thenReturn(Optional.of(creadoPorLaOtraPeticion));   // la otra petición lo creó primero
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("oid duplicado"));

        assertThat(service.obtenerOCrear(token())).isSameAs(creadoPorLaOtraPeticion);
    }

    @Test
    void clienteNuevo_seCreaConLosDatosDelToken() {
        when(repository.findByOid("oid-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Cliente cliente = service.obtenerOCrear(token());

        assertThat(cliente.getNombre()).isEqualTo("Ana");
        assertThat(cliente.getEmail()).isEqualTo("ana@duocuc.cl");
    }
}

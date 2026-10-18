package cl.duoc.pedidos360.service;

import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.repository.ClienteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Obtiene (o crea la primera vez) el cliente que corresponde al usuario del token JWT. */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public Cliente obtenerOCrear(Jwt jwt) {
        // "oid" identifica al usuario en Entra ID; si faltara, se usa "sub"
        String oid = jwt.getClaimAsString("oid");
        if (oid == null) {
            oid = jwt.getSubject();
        }
        final String id = oid;

        String email = jwt.getClaimAsString("preferred_username");
        String nombre = jwt.getClaimAsString("name");
        if (nombre == null || nombre.isBlank()) {
            nombre = email != null ? email : id; // el token de acceso puede no traer "name"
        }
        final String nombreFinal = nombre;

        return clienteRepository.findByOid(id)
                .orElseGet(() -> crear(id, nombreFinal, email));
    }

    /**
     * En el primer login el frontend pide el perfil y los pedidos en paralelo: ambas peticiones pueden
     * intentar crear el mismo cliente. La que pierde choca con la restricción única de "oid" y simplemente
     * lee el cliente que creó la otra.
     */
    private Cliente crear(String oid, String nombre, String email) {
        try {
            return clienteRepository.saveAndFlush(new Cliente(oid, nombre, email));
        } catch (DataIntegrityViolationException e) {
            return clienteRepository.findByOid(oid).orElseThrow(() -> e);
        }
    }
}

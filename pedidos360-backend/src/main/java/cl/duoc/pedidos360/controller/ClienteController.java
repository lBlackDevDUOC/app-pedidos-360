package cl.duoc.pedidos360.controller;

import cl.duoc.pedidos360.model.Cliente;
import cl.duoc.pedidos360.service.ClienteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del cliente autenticado. Los clientes no se crean a mano: salen del login con Microsoft. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /** Devuelve el cliente del usuario logueado (lo crea si es su primer ingreso). */
    @GetMapping("/me")
    public Cliente yo(@AuthenticationPrincipal Jwt jwt) {
        return clienteService.obtenerOCrear(jwt);
    }
}

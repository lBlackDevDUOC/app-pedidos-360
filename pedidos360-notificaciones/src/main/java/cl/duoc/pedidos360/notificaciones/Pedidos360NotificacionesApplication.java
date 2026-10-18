package cl.duoc.pedidos360.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-notificaciones: avisa al cliente por email cuando se crea su pedido y cuando su comprobante está listo. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Pedidos360NotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360NotificacionesApplication.class, args);
    }
}

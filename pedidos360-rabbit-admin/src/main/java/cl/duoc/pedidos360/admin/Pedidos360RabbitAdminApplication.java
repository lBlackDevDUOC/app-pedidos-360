package cl.duoc.pedidos360.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** ms-rabbit-admin: API REST para administrar colas, exchanges y bindings, y monitorear las DLQ. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // revisión periódica de las DLQ (DlqMonitor)
public class Pedidos360RabbitAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360RabbitAdminApplication.class, args);
    }
}

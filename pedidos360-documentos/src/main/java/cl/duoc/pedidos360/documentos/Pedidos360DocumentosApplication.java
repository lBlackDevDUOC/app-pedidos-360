package cl.duoc.pedidos360.documentos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-documentos: escucha los pedidos creados, emite su comprobante y avisa con el evento documento.generado. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Pedidos360DocumentosApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360DocumentosApplication.class, args);
    }
}

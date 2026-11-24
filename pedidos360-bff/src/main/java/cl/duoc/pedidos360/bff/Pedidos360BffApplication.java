package cl.duoc.pedidos360.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ms-bff: Backend For Frontend. Puerta de entrada única y validación del JWT antes de llegar a cada microservicio. */
@SpringBootApplication
public class Pedidos360BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360BffApplication.class, args);
    }
}

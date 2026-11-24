package cl.duoc.pedidos360.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-report: consume orders.events y expone KPIs de operación (ventas por hora, lead time, estados activos). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Pedidos360ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360ReportApplication.class, args);
    }
}

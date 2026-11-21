package cl.duoc.pedidos360.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/** ms-audit: persiste la línea de tiempo de eventos de negocio (Kafka) y la expone en modo solo lectura. */
@SpringBootApplication
@ConfigurationPropertiesScan
// Las páginas de resultados se serializan como DTO estable (content + page), no como PageImpl interno
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class Pedidos360AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360AuditApplication.class, args);
    }
}

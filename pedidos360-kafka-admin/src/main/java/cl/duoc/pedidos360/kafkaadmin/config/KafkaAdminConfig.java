package cl.duoc.pedidos360.kafkaadmin.config;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de administración de Kafka (AdminClient). Se conecta con {@code spring.kafka.bootstrap-servers} y habla
 * directo con los brokers (y el controlador del clúster) para crear/borrar tópicos, cambiar configuraciones y
 * leer consumer groups. Un solo cliente para toda la app (es thread-safe); se cierra al apagar.
 */
@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    public Admin kafkaAdminClient(KafkaProperties kafkaProperties) {
        return Admin.create(kafkaProperties.buildAdminProperties(null));
    }
}

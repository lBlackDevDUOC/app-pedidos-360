package cl.duoc.pedidos360.kafkaadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** ms-kafka-admin: capa de gestión del clúster Kafka (tópicos, configuraciones, consumer groups, offsets). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Pedidos360KafkaAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pedidos360KafkaAdminApplication.class, args);
    }
}

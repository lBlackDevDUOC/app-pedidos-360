package cl.duoc.pedidos360.documentos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Verifica que la mensajería quede aislada de la lógica de negocio (rúbrica: separación de responsabilidades). */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cl.duoc.pedidos360.documentos");
    }

    @Test
    void negocioNoDependeDeRabbitMQ() {
        noClasses().that().resideInAnyPackage("..documentos.service..", "..documentos.model..", "cl.duoc.pedidos360.documentos.config..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.amqp..", "com.rabbitmq..")
                .because("los servicios solo usan la interfaz DocumentoEventPublisher; RabbitMQ vive en ..messaging..")
                .check(clases);
    }

    @Test
    void consumidoresViveEnElPaqueteDeConsumidores() {
        methods().that().areAnnotatedWith(RabbitListener.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..messaging.consumer..")
                .check(clases);
    }

    @Test
    void configuracionDeMensajeriaSoloEnSuPaquete() {
        noClasses().that().resideOutsideOfPackage("..messaging.config..")
                .and().areAnnotatedWith(Configuration.class)
                .should().dependOnClassesThat().resideInAPackage("org.springframework.amqp.core..")
                .allowEmptyShould(true)
                .check(clases);
    }
}

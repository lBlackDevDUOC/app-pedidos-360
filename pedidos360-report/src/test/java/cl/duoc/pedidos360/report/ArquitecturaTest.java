package cl.duoc.pedidos360.report;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Rúbrica: la lógica de procesamiento queda separada de la infraestructura de mensajería. */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cl.duoc.pedidos360.report");
    }

    @Test
    void negocioNoDependeDeKafka() {
        noClasses().that().resideInAnyPackage("..service..", "..model..", "..repository..", "..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.kafka..", "org.apache.kafka..")
                .check(clases);
    }

    @Test
    void listenersSoloEnElPaqueteDeConsumidores() {
        methods().that().areAnnotatedWith(KafkaListener.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..messaging.consumer..")
                .check(clases);
    }
}

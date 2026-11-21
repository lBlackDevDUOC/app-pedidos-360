package cl.duoc.pedidos360.kafkaadmin;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Rúbrica: la lógica de administración está encapsulada en un servicio dedicado. */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cl.duoc.pedidos360.kafkaadmin");
    }

    @Test
    void controladoresNoUsanElAdminClient() {
        noClasses().that().resideInAnyPackage("..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..")
                .because("toda la lógica de Kafka está encapsulada en KafkaAdminService")
                .check(clases);
    }

}

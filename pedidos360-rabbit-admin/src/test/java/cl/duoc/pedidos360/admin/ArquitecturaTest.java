package cl.duoc.pedidos360.admin;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Rúbrica: la lógica de administración está encapsulada en el servicio; los controladores no conocen RabbitMQ. */
class ArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cl.duoc.pedidos360.admin");
    }

    @Test
    void controladoresNoConocenRabbitMQ() {
        noClasses().that().resideInAPackage("..admin.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.amqp..", "com.rabbitmq..", "..admin.rabbit..")
                .because("los controladores solo llaman a métodos de alto nivel de RabbitAdminService")
                .check(clases);
    }

    @Test
    void soloElServicioUsaAmqpAdmin() {
        noClasses().that().resideOutsideOfPackage("..admin.service..")
                .should().dependOnClassesThat().areAssignableTo(AmqpAdmin.class)
                .check(clases);
    }

    @Test
    void dtosSinDependenciasDeInfraestructura() {
        noClasses().that().resideInAPackage("..admin.dto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.amqp..", "com.rabbitmq..", "..admin.rabbit..", "..admin.service..")
                .check(clases);
    }
}

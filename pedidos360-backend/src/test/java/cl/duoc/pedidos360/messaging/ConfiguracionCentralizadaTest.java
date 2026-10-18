package cl.duoc.pedidos360.messaging;

import cl.duoc.pedidos360.messaging.config.RabbitProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Rúbrica: nombres de colas, exchanges y routing keys definidos SOLO en application.yml. */
class ConfiguracionCentralizadaTest {

    private static EnumerablePropertySource<?> yml;

    @BeforeAll
    static void cargarYml() throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yml = (EnumerablePropertySource<?>) fuentes.get(0);
    }

    @Test
    void todaLaTopologiaSeLeeDesdeElYml() {
        RabbitProperties props = new Binder(ConfigurationPropertySources.from(yml))
                .bind("app.rabbitmq", RabbitProperties.class)
                .get();

        assertThat(props.exchanges().eventos()).isNotBlank();
        assertThat(props.exchanges().reintentos()).isNotBlank();
        assertThat(props.exchanges().deadLetter()).isNotBlank();
        assertThat(props.routingKeys().pedidoCreado()).isNotBlank();
        assertThat(props.routingKeys().documentoGenerado()).isNotBlank();
        assertThat(props.colas().documentoGenerado().nombre()).isNotBlank();
        assertThat(props.colas().documentoGenerado().reintento()).isNotBlank();
        assertThat(props.colas().documentoGenerado().dlq()).isNotBlank();
        assertThat(props.reintentos().maximo()).isPositive();
        assertThat(props.retencion().ttlColaMs()).isPositive();
    }

    @Test
    void ningunNombreDeColaOExchangeEstaEscritoEnElCodigo() throws IOException {
        List<String> nombres = Arrays.stream(yml.getPropertyNames())
                .filter(clave -> clave.startsWith("app.rabbitmq.exchanges.")
                        || clave.startsWith("app.rabbitmq.routing-keys.")
                        || clave.startsWith("app.rabbitmq.colas."))
                .map(clave -> String.valueOf(yml.getProperty(clave)))
                .toList();
        assertThat(nombres).isNotEmpty();

        List<String> hallazgos = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(Path.of("src/main/java"))) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String codigo = Files.readString(archivo);
                nombres.stream()
                        .filter(nombre -> codigo.contains('"' + nombre + '"'))
                        .forEach(nombre -> hallazgos.add(archivo.getFileName() + " → \"" + nombre + "\""));
            }
        }
        assertThat(hallazgos).as("Nombres escritos a mano en el código").isEmpty();
    }
}

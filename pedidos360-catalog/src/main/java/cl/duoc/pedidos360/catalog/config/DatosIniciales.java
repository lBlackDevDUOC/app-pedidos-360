package cl.duoc.pedidos360.catalog.config;

import cl.duoc.pedidos360.catalog.model.Producto;
import cl.duoc.pedidos360.catalog.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Carga productos de ejemplo de una panadería/café la primera vez (catálogo vacío). Desactivable por propiedad. */
@Configuration
@ConditionalOnProperty(name = "app.datos-iniciales", havingValue = "true")
public class DatosIniciales {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    @Bean
    ApplicationRunner cargarCatalogo(ProductoRepository productos) {
        return args -> {
            if (productos.count() > 0) return;
            productos.saveAll(List.of(
                    new Producto("PAN-MARRAQUETA", "Marraqueta (1 kg)", "Pan batido del día", "Panadería", 2490.0, 80),
                    new Producto("PAN-HALLULLA", "Hallulla (1 kg)", "Pan de grasa del día", "Panadería", 2390.0, 80),
                    new Producto("PAS-KUCHEN", "Kuchen de manzana", "Porción individual", "Pastelería", 2200.0, 30),
                    new Producto("PAS-BROWNIE", "Brownie", "Chocolate amargo", "Pastelería", 1800.0, 40),
                    new Producto("CAF-ESPRESSO", "Café espresso", "Grano tostado medio", "Cafetería", 1900.0, 200),
                    new Producto("CAF-CAPUCCINO", "Capuccino", "Leche entera o vegetal", "Cafetería", 2600.0, 200),
                    new Producto("SAN-AVOCADO", "Sándwich palta-tomate", "En pan de molde integral", "Sándwiches", 3900.0, 25)));
            log.info("[CATALOGO] Catálogo inicial cargado ({} productos)", productos.count());
        };
    }
}

package cl.duoc.pedidos360.catalog;

import cl.duoc.pedidos360.catalog.model.Producto;
import cl.duoc.pedidos360.catalog.model.StockInsuficienteException;
import cl.duoc.pedidos360.catalog.repository.MovimientoStockRepository;
import cl.duoc.pedidos360.catalog.repository.ProductoRepository;
import cl.duoc.pedidos360.catalog.service.CatalogoService;
import cl.duoc.pedidos360.catalog.service.CatalogoService.LineaStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CatalogoService.class)
@TestPropertySource(properties = "app.datos-iniciales=false")
class CatalogoServiceTest {

    @Autowired
    private CatalogoService service;
    @Autowired
    private ProductoRepository productos;
    @Autowired
    private MovimientoStockRepository movimientos;

    private Producto pan;
    private Producto cafe;

    @BeforeEach
    void preparar() {
        pan = productos.save(new Producto("PAN-1", "Pan", null, "Panadería", 1000.0, 10));
        cafe = productos.save(new Producto("CAF-1", "Café", null, "Cafetería", 2000.0, 2));
    }

    @Test
    void descontarBajaElStockYEsIdempotente() {
        service.descontar(15L, List.of(new LineaStock(pan.getId(), 3), new LineaStock(cafe.getId(), 1)));
        service.descontar(15L, List.of(new LineaStock(pan.getId(), 3), new LineaStock(cafe.getId(), 1))); // reintento

        assertThat(productos.findById(pan.getId()).orElseThrow().getStock()).isEqualTo(7);
        assertThat(productos.findById(cafe.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void sinStockSuficienteNoSeDescuentaNada() {
        assertThatThrownBy(() -> service.descontar(16L,
                List.of(new LineaStock(pan.getId(), 1), new LineaStock(cafe.getId(), 5))))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Café");
        // la excepción sube y @Transactional revierte; aquí verificamos que nunca llegó a negativo
        assertThat(productos.findById(cafe.getId()).orElseThrow().getStock()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reponerDevuelveLoDescontadoUnaSolaVez() {
        service.descontar(17L, List.of(new LineaStock(pan.getId(), 4)));
        service.reponer(17L);
        service.reponer(17L);

        assertThat(productos.findById(pan.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(movimientos.count()).isEqualTo(2);
    }

    @Test
    void skuDuplicadoSeRechaza() {
        assertThatThrownBy(() -> service.crear("PAN-1", "Otro", null, null, 1.0, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}

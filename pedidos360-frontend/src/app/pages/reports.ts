import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportesApi } from '../core/api';
import { Kpis, TopProducto, mensajeError } from '../core/modelos';

/**
 * /reports (Admin): panel de operaciones. Los datos los calcula ms-report consumiendo orders.events de Kafka;
 * el panel consulta cada 5 s, así un pedido aceptado o entregado aparece casi en tiempo real sin recargar.
 * Incluye SalesChart (ventas por hora), LeadTime y TopProducts.
 */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  template: `
    <h2>Reportería en tiempo real</h2>
    <div class="filtros">
      <label>Rango
        <select [(ngModel)]="rango" (change)="actualizar()" name="rango">
          <option value="last24h">Últimas 24 h</option>
          <option value="last7d">Últimos 7 días</option>
          <option value="last30d">Últimos 30 días</option>
        </select>
      </label>
      <span class="nota">Se actualiza cada 5 s
        @if (kpis()?.ultimoEventoEn) { · último evento de Kafka {{ kpis()!.ultimoEventoEn | date: 'HH:mm:ss' }} }
      </span>
    </div>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    @if (kpis(); as k) {
      <div class="tarjetas">
        <div class="tarjeta"><span>Ventas</span><strong>\${{ k.ventasTotales | number: '1.0-0' }}</strong></div>
        <div class="tarjeta"><span>Ticket promedio</span><strong>\${{ k.ticketPromedio | number: '1.0-0' }}</strong></div>
        <div class="tarjeta"><span>Lead time promedio</span><strong>{{ k.leadTimePromedioMinutos ?? '—' }} min</strong></div>
        <div class="tarjeta"><span>Creados / entregados / cancelados</span>
          <strong>{{ k.pedidosCreados }} / {{ k.pedidosEntregados }} / {{ k.pedidosCancelados }}</strong></div>
      </div>

      <section class="panel">
        <h3>Ventas por hora</h3>
        <div class="barras">
          @for (v of ventasVisibles(); track v.hora) {
            <div class="barra" [title]="(v.hora | date: 'dd/MM HH:00') + ' · ' + v.pedidos + ' pedidos · $' + v.monto">
              <div class="relleno" [style.height.%]="altura(v.monto)"></div>
              <small>{{ v.hora | date: 'HH' }}</small>
            </div>
          }
        </div>
      </section>

      <div class="dos-columnas">
        <section class="panel">
          <h3>Pedidos activos por estado</h3>
          <table class="tabla">
            @for (e of estados(k); track e[0]) {
              <tr><td>{{ e[0] }}</td><td><strong>{{ e[1] }}</strong></td></tr>
            }
          </table>
        </section>
        <section class="panel">
          <h3>Top productos</h3>
          <table class="tabla">
            <thead><tr><th>Producto</th><th>Unidades</th><th>Monto</th></tr></thead>
            @for (t of top(); track t.productoId) {
              <tr><td>{{ t.nombre }}</td><td>{{ t.unidades }}</td><td>\${{ t.monto | number: '1.0-0' }}</td></tr>
            } @empty {
              <tr><td colspan="3">Sin ventas con productos en el rango.</td></tr>
            }
          </table>
        </section>
      </div>
    }
  `,
})
export class ReportsPage implements OnInit, OnDestroy {
  private api = inject(ReportesApi);
  private intervalo?: ReturnType<typeof setInterval>;

  rango = 'last24h';
  kpis = signal<Kpis | null>(null);
  top = signal<TopProducto[]>([]);
  error = signal('');

  /** En 7 y 30 días hay demasiadas horas para el gráfico: se muestran las últimas 48. */
  ventasVisibles = computed(() => (this.kpis()?.ventasPorHora ?? []).slice(-48));
  private maximo = computed(() => Math.max(1, ...this.ventasVisibles().map((v) => v.monto)));

  ngOnInit(): void {
    this.actualizar();
    this.intervalo = setInterval(() => this.actualizar(), 5000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervalo);
  }

  actualizar(): void {
    this.api.kpis(this.rango).subscribe({
      next: (k) => {
        this.kpis.set(k);
        this.error.set('');
      },
      error: (e) => this.error.set(mensajeError(e, 'No se pudo consultar ms-report')),
    });
    this.api.topProductos(this.rango).subscribe({
      next: (t) => this.top.set(t),
      error: () => this.top.set([]),
    });
  }

  altura(monto: number): number {
    return Math.round((monto / this.maximo()) * 100);
  }

  estados(k: Kpis): [string, number][] {
    return Object.entries(k.estadosActivos);
  }
}

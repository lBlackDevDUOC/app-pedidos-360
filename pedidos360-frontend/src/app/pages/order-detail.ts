import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AuditoriaApi, PedidosApi } from '../core/api';
import { EstadoPedido, Pedido, RegistroAuditoria, SIGUIENTES, mensajeError } from '../core/modelos';
import { EstadoBadge } from './estado-badge';

/**
 * /orders/:id (OrderDetailComponent): detalle, botones para avanzar el estado según rol y la trazabilidad del
 * pedido que arma ms-audit desde Kafka (Admin/Auditor).
 */
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, EstadoBadge],
  template: `
    <p><a routerLink="/orders">← Volver a pedidos</a></p>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (pedido(); as p) {
      <h2>Pedido #{{ p.id }} <app-estado-badge [estado]="p.estado" /></h2>
      <p>Cliente: <strong>{{ p.cliente.nombre }}</strong> · Creado {{ p.fechaCreacion | date: 'dd/MM/yyyy HH:mm' }}
        @if (p.fechaEntrega) { · Entregado {{ p.fechaEntrega | date: 'dd/MM HH:mm' }} }</p>
      <p>Comprobante: {{ p.numeroComprobante ?? 'generándose (RabbitMQ)…' }}</p>

      <table class="tabla">
        <thead><tr><th>Producto</th><th>Cantidad</th><th>Precio</th><th>Subtotal</th></tr></thead>
        <tbody>
          @for (i of p.items; track i.productoId) {
            <tr><td>{{ i.nombre }}</td><td>{{ i.cantidad }}</td><td>\${{ i.precioUnitario | number: '1.0-0' }}</td>
              <td>\${{ i.cantidad * i.precioUnitario | number: '1.0-0' }}</td></tr>
          } @empty {
            <tr><td colspan="4">Pedido de monto libre</td></tr>
          }
        </tbody>
        <tfoot><tr><td colspan="3">Total</td><td><strong>\${{ p.total | number: '1.0-0' }}</strong></td></tr></tfoot>
      </table>

      <div class="acciones">
        @for (destino of accionesPermitidas(p); track destino) {
          <button type="button" class="btn" [class.peligro]="destino === 'CANCELADO'" (click)="cambiar(destino)">
            {{ destino === 'CANCELADO' ? 'Cancelar pedido' : 'Pasar a ' + destino.replace('_', ' ') }}
          </button>
        }
      </div>

      @if (auth.tieneRol('Admin', 'Auditor')) {
        <h3>Trazabilidad (ms-audit, desde Kafka)</h3>
        <ul class="timeline">
          @for (r of timeline(); track r.eventoId) {
            <li><time>{{ r.ocurridoEn | date: 'dd/MM HH:mm:ss' }}</time>
              <strong>{{ r.accion }}</strong> {{ r.detalle }}
              <small>{{ r.actorNombre ?? r.actorId }} {{ r.ip ? '· ' + r.ip : '' }} · {{ r.fuente }}</small></li>
          } @empty {
            <li>Sin eventos todavía.</li>
          }
        </ul>
      }
    }
  `,
})
export class OrderDetailPage implements OnInit {
  /** Parámetro :id de la ruta (withComponentInputBinding). */
  id = input.required<string>();

  auth = inject(AuthService);
  private pedidosApi = inject(PedidosApi);
  private auditoriaApi = inject(AuditoriaApi);

  pedido = signal<Pedido | null>(null);
  timeline = signal<RegistroAuditoria[]>([]);
  error = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.pedidosApi.obtener(Number(this.id())).subscribe({
      next: (p) => this.pedido.set(p),
      error: (e) => this.error.set(mensajeError(e, 'No se pudo cargar el pedido')),
    });
    if (this.auth.tieneRol('Admin', 'Auditor')) {
      this.auditoriaApi.timelinePedido(Number(this.id())).subscribe({
        next: (t) => this.timeline.set(t),
        error: () => this.timeline.set([]),
      });
    }
  }

  /** Operador/Admin: cualquier transición válida. Cliente: solo cancelar. */
  accionesPermitidas(p: Pedido): EstadoPedido[] {
    const siguientes = SIGUIENTES[p.estado];
    return this.auth.tieneRol('Operador', 'Admin') ? siguientes : siguientes.filter((e) => e === 'CANCELADO');
  }

  cambiar(destino: EstadoPedido): void {
    this.error.set('');
    this.pedidosApi.cambiarEstado(Number(this.id()), destino).subscribe({
      next: (p) => {
        this.pedido.set(p);
        // ms-audit recibe el evento por Kafka en milisegundos: se recarga la trazabilidad
        setTimeout(() => this.cargar(), 1000);
      },
      error: (e) => this.error.set(mensajeError(e, 'No se pudo cambiar el estado')),
    });
  }
}

import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { CatalogoApi, PedidosApi } from '../core/api';
import { ESTADOS, EstadoPedido, Pedido, Producto, mensajeError } from '../core/modelos';
import { EstadoBadge } from './estado-badge';

/**
 * /orders: lista filtrable de pedidos (OrderListComponent) y formulario de nuevo pedido con productos del
 * catálogo (solo Cliente u Operador). El Cliente ve sus pedidos; Operador y Admin ven todos (lo decide el backend).
 */
@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, EstadoBadge],
  template: `
    <h2>Pedidos</h2>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (mensaje()) {
      <p class="ok">{{ mensaje() }}</p>
    }

    @if (auth.tieneRol('Cliente', 'Operador')) {
      <section class="panel">
        <h3>Nuevo pedido</h3>
        <div class="productos">
          @for (p of productos(); track p.id) {
            <label class="producto">
              <span>{{ p.nombre }} <small>\${{ p.precio | number: '1.0-0' }} · stock {{ p.stock }}</small></span>
              <input type="number" min="0" max="100" [(ngModel)]="cantidades[p.id]" [name]="'cant-' + p.id" />
            </label>
          } @empty {
            <p class="nota">Cargando catálogo…</p>
          }
        </div>
        <p>Total estimado: <strong>\${{ totalEstimado() | number: '1.0-0' }}</strong>
          <small class="nota">(el total real lo calcula el backend con los precios del catálogo)</small></p>
        <button type="button" class="btn primario" (click)="crear()" [disabled]="totalEstimado() === 0">Crear pedido</button>
      </section>
    }

    <section class="panel">
      <div class="filtros">
        <label>Estado
          <select [(ngModel)]="filtro" (change)="cargar()" name="filtro">
            <option value="">Todos</option>
            @for (e of estados; track e) {
              <option [value]="e">{{ e }}</option>
            }
          </select>
        </label>
        <button type="button" class="btn" (click)="cargar()">Actualizar</button>
      </div>
      <table class="tabla">
        <thead><tr><th>#</th><th>Cliente</th><th>Productos</th><th>Total</th><th>Estado</th><th>Comprobante</th><th>Fecha</th></tr></thead>
        <tbody>
          @for (p of pedidos(); track p.id) {
            <tr>
              <td><a [routerLink]="['/orders', p.id]">{{ p.id }}</a></td>
              <td>{{ p.cliente.nombre }}</td>
              <td>{{ resumen(p) }}</td>
              <td>\${{ p.total | number: '1.0-0' }}</td>
              <td><app-estado-badge [estado]="p.estado" /></td>
              <td>{{ p.numeroComprobante ?? 'Generando…' }}</td>
              <td>{{ p.fechaCreacion | date: 'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="7">{{ cargando() ? 'Cargando…' : 'No hay pedidos.' }}</td></tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class OrdersPage implements OnInit {
  auth = inject(AuthService);
  private pedidosApi = inject(PedidosApi);
  private catalogoApi = inject(CatalogoApi);

  estados = ESTADOS;
  filtro: EstadoPedido | '' = '';
  cantidades: Record<number, number> = {};

  pedidos = signal<Pedido[]>([]);
  productos = signal<Producto[]>([]);
  cargando = signal(false);
  error = signal('');
  mensaje = signal('');

  ngOnInit(): void {
    this.cargar();
    if (this.auth.tieneRol('Cliente', 'Operador')) {
      this.catalogoApi.listar().subscribe({
        next: (p) => this.productos.set(p),
        error: (e) => this.error.set(mensajeError(e, 'No se pudo cargar el catálogo')),
      });
    }
  }

  cargar(): void {
    this.cargando.set(true);
    this.pedidosApi.listar(this.filtro).subscribe({
      next: (p) => {
        this.pedidos.set(p);
        this.cargando.set(false);
      },
      error: (e) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No se pudieron cargar los pedidos'));
      },
    });
  }

  totalEstimado(): number {
    return this.productos().reduce((t, p) => t + (this.cantidades[p.id] || 0) * p.precio, 0);
  }

  crear(): void {
    const items = Object.entries(this.cantidades)
      .filter(([, c]) => c > 0)
      .map(([id, cantidad]) => ({ productoId: Number(id), cantidad }));
    this.error.set('');
    this.pedidosApi.crear(items).subscribe({
      next: (p) => {
        this.mensaje.set(`Pedido #${p.id} creado por $${p.total}. El comprobante se genera en segundo plano.`);
        this.cantidades = {};
        this.cargar();
      },
      error: (e) => this.error.set(mensajeError(e, 'No se pudo crear el pedido')),
    });
  }

  resumen(p: Pedido): string {
    return p.items?.length ? p.items.map((i) => `${i.cantidad}× ${i.nombre}`).join(', ') : 'Monto libre';
  }
}

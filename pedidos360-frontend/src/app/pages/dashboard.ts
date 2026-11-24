import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { PedidosApi, ReportesApi } from '../core/api';
import { Kpis, Pedido, mensajeError } from '../core/modelos';
import { EstadoBadge } from './estado-badge';

/** /dashboard: resumen según el rol (Admin: KPIs globales, Operador: pedidos en curso, Cliente: sus últimos pedidos). */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, EstadoBadge],
  template: `
    <h2>Hola, {{ auth.nombre() }}</h2>
    <p class="nota">Roles: {{ auth.roles().join(', ') }}</p>
    @if (sinPermiso) {
      <p class="alerta">Tu rol no tiene acceso a esa sección.</p>
    }
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    @if (kpis(); as k) {
      <div class="tarjetas">
        <div class="tarjeta"><span>Ventas 24 h</span><strong>\${{ k.ventasTotales | number: '1.0-0' }}</strong></div>
        <div class="tarjeta"><span>Pedidos 24 h</span><strong>{{ k.pedidosCreados }}</strong></div>
        <div class="tarjeta"><span>Lead time</span><strong>{{ k.leadTimePromedioMinutos ?? '—' }} min</strong></div>
        <div class="tarjeta"><span>En curso</span><strong>{{ enCurso(k) }}</strong></div>
      </div>
      <p><a routerLink="/reports">Ver panel de reportería en tiempo real →</a></p>
    }

    @if (!auth.tieneRol('Admin') || auth.tieneRol('Operador')) {
      <h3>{{ auth.tieneRol('Operador') ? 'Pedidos en curso' : 'Tus últimos pedidos' }}</h3>
      <table class="tabla">
        <thead><tr><th>#</th><th>Cliente</th><th>Total</th><th>Estado</th><th>Fecha</th></tr></thead>
        <tbody>
          @for (p of pedidos(); track p.id) {
            <tr>
              <td><a [routerLink]="['/orders', p.id]">{{ p.id }}</a></td>
              <td>{{ p.cliente.nombre }}</td>
              <td>\${{ p.total | number: '1.0-0' }}</td>
              <td><app-estado-badge [estado]="p.estado" /></td>
              <td>{{ p.fechaCreacion | date: 'dd/MM HH:mm' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="5">Sin pedidos.</td></tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class DashboardPage implements OnInit {
  auth = inject(AuthService);
  private pedidosApi = inject(PedidosApi);
  private reportesApi = inject(ReportesApi);
  sinPermiso = !!inject(ActivatedRoute).snapshot.queryParamMap.get('sinPermiso');

  pedidos = signal<Pedido[]>([]);
  kpis = signal<Kpis | null>(null);
  error = signal('');

  ngOnInit(): void {
    if (this.auth.tieneRol('Admin')) {
      this.reportesApi.kpis('last24h').subscribe({
        next: (k) => this.kpis.set(k),
        error: (e) => this.error.set(mensajeError(e, 'No se pudieron cargar los KPIs')),
      });
    }
    if (!this.auth.tieneRol('Admin') || this.auth.tieneRol('Operador')) {
      this.pedidosApi.listar().subscribe({
        next: (lista) => {
          const filtrados = this.auth.tieneRol('Operador')
            ? lista.filter((p) => p.estado !== 'ENTREGADO' && p.estado !== 'CANCELADO')
            : lista;
          this.pedidos.set(filtrados.slice(0, 10));
        },
        error: (e) => this.error.set(mensajeError(e, 'No se pudieron cargar los pedidos')),
      });
    }
  }

  enCurso(k: Kpis): number {
    return Object.values(k.estadosActivos).reduce((a, b) => a + b, 0);
  }
}

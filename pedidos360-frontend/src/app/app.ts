import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';
import { Cliente, Pedido, PedidoRequest, PedidoService } from './pedido.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  styleUrl: './app.css',
})
export class App implements OnInit {
  title = 'Pedidos360 - Sistema de Gestión';

  private msalService = inject(MsalService);
  private pedidoService = inject(PedidoService);

  perfil = signal<Cliente | null>(null);
  pedidos = signal<Pedido[]>([]);
  cargando = signal(false);
  error = signal('');

  nuevoPedido: PedidoRequest = { total: 0, estado: 'PENDIENTE' };
  estados = ['PENDIENTE', 'EN_CAMINO', 'ENTREGADO'];

  ngOnInit(): void {
    // Los datos solo se cargan si el usuario ya inició sesión con Microsoft
    if (this.isLoggedIn()) {
      this.cargarTodo();
    }
  }

  isLoggedIn(): boolean {
    return this.msalService.instance.getAllAccounts().length > 0;
  }

  getUserName(): string {
    const accounts = this.msalService.instance.getAllAccounts();
    return accounts.length > 0 ? accounts[0].username : '';
  }

  login(): void {
    this.msalService.loginRedirect();
  }

  logout(): void {
    this.msalService.logoutRedirect();
  }

  cargarTodo(): void {
    this.pedidoService.obtenerPerfil().subscribe({
      next: (cliente) => this.perfil.set(cliente),
      error: (err) => this.mostrarError('No se pudo cargar tu perfil', err),
    });
    this.cargarPedidos();
  }

  cargarPedidos(): void {
    this.cargando.set(true);
    this.error.set('');
    this.pedidoService.listarPedidos().subscribe({
      next: (data) => {
        this.pedidos.set(data);
        this.cargando.set(false);
      },
      error: (err) => this.mostrarError('No se pudieron cargar los pedidos', err),
    });
  }

  crearPedido(): void {
    this.error.set('');
    this.pedidoService.crearPedido(this.nuevoPedido).subscribe({
      next: () => {
        this.nuevoPedido = { total: 0, estado: 'PENDIENTE' };
        this.cargarPedidos();
      },
      error: (err) => this.mostrarError('No se pudo crear el pedido', err),
    });
  }

  private mostrarError(mensaje: string, err: { status?: number }): void {
    this.cargando.set(false);
    const detalle =
      err.status === 403 ? ' (sin permiso: falta el scope OT.Create)' : err.status ? ` (HTTP ${err.status})` : '';
    this.error.set(mensaje + detalle);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface Cliente {
  id: number;
  nombre: string;
  email?: string;
}

export interface Pedido {
  id: number;
  cliente: Cliente;
  total: number;
  estado: string;
  fechaCreacion: string;
}

/** Cuerpo del POST /api/pedidos: el cliente lo toma el backend del token de Microsoft. */
export interface PedidoRequest {
  total: number;
  estado: string;
}

/** Consume la API. MsalInterceptor agrega el Bearer token automáticamente. */
@Injectable({ providedIn: 'root' })
export class PedidoService {
  private http = inject(HttpClient);
  private api = `${environment.apiBaseUrl}/api`;

  /** Perfil del usuario logueado (el backend lo crea en su primer ingreso). */
  obtenerPerfil(): Observable<Cliente> {
    return this.http.get<Cliente>(`${this.api}/clientes/me`);
  }

  listarPedidos(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.api}/pedidos`);
  }

  crearPedido(pedido: PedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.api}/pedidos`, pedido);
  }
}

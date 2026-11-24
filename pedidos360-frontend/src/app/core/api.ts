import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Cliente, EstadoPedido, Kpis, Pagina, Pedido, Producto, RegistroAuditoria, TopProducto } from './modelos';

/**
 * Clientes HTTP de cada microservicio. Todos usan la misma URL base (el BFF a través del API Gateway);
 * el MsalInterceptor agrega "Authorization: Bearer <access_token>" a cada llamada.
 */
const API = `${environment.apiBaseUrl}/api`;

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private http = inject(HttpClient);

  perfil(): Observable<Cliente> {
    return this.http.get<Cliente>(`${API}/clientes/me`);
  }

  listar(estado?: EstadoPedido | ''): Observable<Pedido[]> {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<Pedido[]>(`${API}/pedidos`, { params });
  }

  obtener(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(`${API}/pedidos/${id}`);
  }

  crear(items: { productoId: number; cantidad: number }[]): Observable<Pedido> {
    return this.http.post<Pedido>(`${API}/pedidos`, { items });
  }

  cambiarEstado(id: number, estado: EstadoPedido): Observable<Pedido> {
    return this.http.put<Pedido>(`${API}/pedidos/${id}/estado`, { estado });
  }
}

@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private http = inject(HttpClient);

  listar(incluirInactivos = false): Observable<Producto[]> {
    return this.http.get<Producto[]>(`${API}/catalog/products`, { params: { incluirInactivos } });
  }

  crear(p: Omit<Producto, 'id' | 'activo'>): Observable<Producto> {
    return this.http.post<Producto>(`${API}/catalog/products`, p);
  }

  actualizar(id: number, cambios: Partial<Producto>): Observable<Producto> {
    return this.http.put<Producto>(`${API}/catalog/products/${id}`, cambios);
  }
}

@Injectable({ providedIn: 'root' })
export class ReportesApi {
  private http = inject(HttpClient);

  kpis(range: string): Observable<Kpis> {
    return this.http.get<Kpis>(`${API}/report/kpis`, { params: { range } });
  }

  topProductos(range: string, limit = 5): Observable<TopProducto[]> {
    return this.http.get<TopProducto[]>(`${API}/report/top-products`, { params: { range, limit } });
  }
}

@Injectable({ providedIn: 'root' })
export class AuditoriaApi {
  private http = inject(HttpClient);

  buscar(filtros: { usuario?: string; tipo?: string; entidadId?: string; desde?: string; hasta?: string },
         page = 0, size = 50): Observable<Pagina<RegistroAuditoria>> {
    let params = new HttpParams().set('page', page).set('size', size);
    Object.entries(filtros).forEach(([k, v]) => {
      if (v) params = params.set(k, v);
    });
    return this.http.get<Pagina<RegistroAuditoria>>(`${API}/audit`, { params });
  }

  timelinePedido(id: number): Observable<RegistroAuditoria[]> {
    return this.http.get<RegistroAuditoria[]>(`${API}/audit/pedidos/${id}`);
  }
}

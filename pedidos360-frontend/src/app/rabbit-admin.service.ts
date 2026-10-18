import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

/** Estado de una cola según ms-rabbit-admin. */
export interface Cola {
  nombre: string;
  durable: boolean;
  mensajes: number;
  /** Esperando a un consumidor. */
  mensajesListos: number;
  /** Entregados a un consumidor y aún sin ACK. */
  mensajesSinConfirmar: number;
  consumidores: number;
  esDlq: boolean;
  argumentos: Record<string, unknown>;
}

export interface AlertaDlq {
  cola: string;
  mensajes: number;
  umbral: number;
  detectadaEn: string;
}

/** Consume ms-rabbit-admin. MsalInterceptor agrega el Bearer token (mismo scope que ms-pedidos). */
@Injectable({ providedIn: 'root' })
export class RabbitAdminService {
  private http = inject(HttpClient);
  private api = `${environment.adminApiBaseUrl}/api/admin`;

  listarColas(): Observable<Cola[]> {
    return this.http.get<Cola[]>(`${this.api}/queues`);
  }

  listarAlertas(): Observable<AlertaDlq[]> {
    return this.http.get<AlertaDlq[]>(`${this.api}/alerts`);
  }

  /** Vacía una cola (útil para limpiar una DLQ ya revisada). */
  purgar(nombre: string): Observable<{ cola: string; mensajesEliminados: number }> {
    return this.http.delete<{ cola: string; mensajesEliminados: number }>(
      `${this.api}/queues/${encodeURIComponent(nombre)}/messages`,
    );
  }
}

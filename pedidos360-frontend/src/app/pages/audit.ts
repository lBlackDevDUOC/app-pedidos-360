import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuditoriaApi } from '../core/api';
import { RegistroAuditoria, mensajeError } from '../core/modelos';

/**
 * /audit (Admin, Auditor): línea de tiempo de eventos de negocio que ms-audit guarda desde Kafka.
 * Filtros: usuario, tipo de evento y rango de fechas. Solo lectura.
 */
@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <h2>Auditoría</h2>
    <div class="filtros">
      <label>Usuario <input [(ngModel)]="usuario" name="usuario" placeholder="nombre u oid" /></label>
      <label>Tipo
        <select [(ngModel)]="tipo" name="tipo">
          <option value="">Todos</option>
          @for (t of tipos; track t) {
            <option [value]="t">{{ t }}</option>
          }
        </select>
      </label>
      <label>Desde <input type="datetime-local" [(ngModel)]="desde" name="desde" /></label>
      <label>Hasta <input type="datetime-local" [(ngModel)]="hasta" name="hasta" /></label>
      <button type="button" class="btn primario" (click)="buscar(0)">Buscar</button>
    </div>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <table class="tabla">
      <thead><tr><th>Cuándo</th><th>Qué</th><th>Sobre</th><th>Detalle</th><th>Quién</th><th>Desde dónde</th><th>Tópico</th></tr></thead>
      <tbody>
        @for (r of registros(); track r.eventoId) {
          <tr>
            <td>{{ r.ocurridoEn | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
            <td>{{ r.accion }}</td>
            <td><a [routerLink]="['/orders', r.entidadId]">{{ r.entidad }} #{{ r.entidadId }}</a></td>
            <td>{{ r.detalle }}</td>
            <td>{{ r.actorNombre ?? r.actorId }} <small>{{ r.actorRoles }}</small></td>
            <td>{{ r.ip }}</td>
            <td><small>{{ r.fuente }}</small></td>
          </tr>
        } @empty {
          <tr><td colspan="7">Sin registros para esos filtros.</td></tr>
        }
      </tbody>
    </table>
    <div class="paginacion">
      <button type="button" class="btn" [disabled]="pagina() === 0" (click)="buscar(pagina() - 1)">Anterior</button>
      <span>Página {{ pagina() + 1 }} de {{ totalPaginas() || 1 }}</span>
      <button type="button" class="btn" [disabled]="pagina() + 1 >= totalPaginas()" (click)="buscar(pagina() + 1)">Siguiente</button>
    </div>
  `,
})
export class AuditPage implements OnInit {
  private api = inject(AuditoriaApi);

  tipos = ['PEDIDO_CREADO', 'PEDIDO_ESTADO_CAMBIADO', 'OrderCreated', 'OrderAccepted', 'OrderPreparing',
    'OrderDispatched', 'OrderDelivered', 'OrderCancelled'];
  usuario = '';
  tipo = '';
  desde = '';
  hasta = '';

  registros = signal<RegistroAuditoria[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  error = signal('');

  ngOnInit(): void {
    this.buscar(0);
  }

  buscar(pagina: number): void {
    this.error.set('');
    this.api.buscar({
      usuario: this.usuario, tipo: this.tipo,
      // datetime-local no trae zona horaria: se convierte a ISO UTC, que es lo que espera ms-audit
      desde: this.desde ? new Date(this.desde).toISOString() : '',
      hasta: this.hasta ? new Date(this.hasta).toISOString() : '',
    }, pagina).subscribe({
      next: (p) => {
        this.registros.set(p.content);
        this.pagina.set(p.page.number);
        this.totalPaginas.set(p.page.totalPages);
      },
      error: (e) => this.error.set(mensajeError(e, 'No se pudo consultar ms-audit')),
    });
  }
}

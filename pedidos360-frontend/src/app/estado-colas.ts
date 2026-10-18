import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { AlertaDlq, Cola, RabbitAdminService } from './rabbit-admin.service';

/**
 * Panel "Estado de RabbitMQ": colas con mensajes listos / sin confirmar / consumidores y alertas de DLQ.
 * Se actualiza solo cada 5 segundos para ver en vivo cómo se mueven los mensajes.
 */
@Component({
  selector: 'app-estado-colas',
  standalone: true,
  templateUrl: './estado-colas.html',
})
export class EstadoColas implements OnInit, OnDestroy {
  private adminService = inject(RabbitAdminService);
  private intervalo?: ReturnType<typeof setInterval>;

  colas = signal<Cola[]>([]);
  alertas = signal<AlertaDlq[]>([]);
  error = signal('');
  ultimaActualizacion = signal<Date | null>(null);

  ngOnInit(): void {
    this.actualizar();
    this.intervalo = setInterval(() => this.actualizar(), 5000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervalo);
  }

  actualizar(): void {
    this.adminService.listarColas().subscribe({
      next: (colas) => {
        // Solo las colas de trabajo y DLQ; las .retry son internas y casi siempre están vacías
        this.colas.set(colas.filter((c) => !c.nombre.endsWith('.retry')));
        this.ultimaActualizacion.set(new Date());
        this.error.set('');
      },
      error: (err: { status?: number }) =>
        this.error.set(`No se pudo consultar ms-rabbit-admin${err.status ? ` (HTTP ${err.status})` : ''}`),
    });
    this.adminService.listarAlertas().subscribe({
      next: (alertas) => this.alertas.set(alertas),
      error: () => this.alertas.set([]),
    });
  }

  purgar(cola: Cola): void {
    if (!confirm(`¿Eliminar los ${cola.mensajes} mensajes de ${cola.nombre}?`)) {
      return;
    }
    this.adminService.purgar(cola.nombre).subscribe({
      next: () => this.actualizar(),
      error: () => this.error.set(`No se pudo purgar ${cola.nombre}`),
    });
  }
}

import { Component, input } from '@angular/core';
import { EstadoPedido } from '../core/modelos';

/** Etiqueta de color según el estado del pedido (OrderStatusBadgeComponent del caso). */
@Component({
  selector: 'app-estado-badge',
  standalone: true,
  template: `<span class="badge" [class]="'badge estado-' + estado().toLowerCase()">{{ etiqueta() }}</span>`,
})
export class EstadoBadge {
  estado = input.required<EstadoPedido>();

  etiqueta(): string {
    return this.estado().replace('_', ' ');
  }
}

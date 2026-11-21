import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { CatalogoApi } from '../core/api';
import { Producto, mensajeError } from '../core/modelos';

/**
 * /catalog (Admin, Operador): grilla de productos (ProductCardComponent) y formulario de alta/edición
 * (ProductFormComponent, solo Admin). El stock baja solo cuando un Operador acepta un pedido.
 */
@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  template: `
    <h2>Catálogo</h2>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <div class="grilla">
      @for (p of productos(); track p.id) {
        <article class="producto-card" [class.inactivo]="!p.activo">
          <small>{{ p.categoria }} · {{ p.sku }}</small>
          <h4>{{ p.nombre }}</h4>
          <p>{{ p.descripcion }}</p>
          <p><strong>\${{ p.precio | number: '1.0-0' }}</strong> ·
            <span [class.bajo]="p.stock < 10">stock {{ p.stock }}</span></p>
          @if (esAdmin) {
            <button type="button" class="btn" (click)="editar(p)">Editar</button>
          }
        </article>
      }
    </div>

    @if (esAdmin) {
      <section class="panel">
        <h3>{{ editando ? 'Editar ' + form.nombre : 'Nuevo producto' }}</h3>
        <div class="formulario">
          <label>SKU <input [(ngModel)]="form.sku" name="sku" [disabled]="!!editando" placeholder="PAN-CIABATTA" /></label>
          <label>Nombre <input [(ngModel)]="form.nombre" name="nombre" /></label>
          <label>Categoría <input [(ngModel)]="form.categoria" name="categoria" /></label>
          <label>Precio <input type="number" [(ngModel)]="form.precio" name="precio" /></label>
          <label>Stock <input type="number" [(ngModel)]="form.stock" name="stock" /></label>
          <label>Descripción <input [(ngModel)]="form.descripcion" name="descripcion" /></label>
          @if (editando) {
            <label class="check"><input type="checkbox" [(ngModel)]="form.activo" name="activo" /> Activo</label>
          }
        </div>
        <button type="button" class="btn primario" (click)="guardar()">Guardar</button>
        @if (editando) {
          <button type="button" class="btn" (click)="limpiar()">Cancelar</button>
        }
      </section>
    }
  `,
})
export class CatalogPage implements OnInit {
  private api = inject(CatalogoApi);
  esAdmin = inject(AuthService).tieneRol('Admin');

  productos = signal<Producto[]>([]);
  error = signal('');
  editando: number | null = null;
  form: Partial<Producto> = {};

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.api.listar(true).subscribe({
      next: (p) => this.productos.set(p),
      error: (e) => this.error.set(mensajeError(e, 'No se pudo cargar el catálogo')),
    });
  }

  editar(p: Producto): void {
    this.editando = p.id;
    this.form = { ...p };
  }

  limpiar(): void {
    this.editando = null;
    this.form = {};
  }

  guardar(): void {
    this.error.set('');
    const peticion = this.editando
      ? this.api.actualizar(this.editando, {
          nombre: this.form.nombre, categoria: this.form.categoria, descripcion: this.form.descripcion,
          precio: this.form.precio, stock: this.form.stock, activo: this.form.activo,
        })
      : this.api.crear(this.form as Omit<Producto, 'id' | 'activo'>);
    peticion.subscribe({
      next: () => {
        this.limpiar();
        this.cargar();
      },
      error: (e) => this.error.set(mensajeError(e, 'No se pudo guardar el producto')),
    });
  }
}

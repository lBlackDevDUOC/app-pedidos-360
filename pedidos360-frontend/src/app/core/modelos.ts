/** Contratos de la API (lo que devuelve cada microservicio a través del BFF). */

export type EstadoPedido = 'CREADO' | 'ACEPTADO' | 'EN_PREPARACION' | 'DESPACHADO' | 'ENTREGADO' | 'CANCELADO';

export const ESTADOS: EstadoPedido[] = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO', 'CANCELADO'];

/** Mismas transiciones que el enum EstadoPedido del backend (el backend vuelve a validarlas). */
export const SIGUIENTES: Record<EstadoPedido, EstadoPedido[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'],
  DESPACHADO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
};

export interface Cliente {
  id: number;
  nombre: string;
  email?: string;
}

export interface ItemPedido {
  productoId: number;
  nombre: string;
  cantidad: number;
  precioUnitario: number;
}

export interface Pedido {
  id: number;
  cliente: Cliente;
  total: number;
  estado: EstadoPedido;
  items: ItemPedido[];
  fechaCreacion: string;
  fechaActualizacion?: string;
  fechaEntrega?: string;
  /** Lo completa ms-documentos de forma asíncrona (RabbitMQ). */
  numeroComprobante?: string;
}

export interface Producto {
  id: number;
  sku: string;
  nombre: string;
  descripcion?: string;
  categoria?: string;
  precio: number;
  stock: number;
  activo: boolean;
}

export interface VentaHora {
  hora: string;
  pedidos: number;
  monto: number;
}

export interface Kpis {
  rango: string;
  pedidosCreados: number;
  pedidosEntregados: number;
  pedidosCancelados: number;
  ventasTotales: number;
  ticketPromedio: number;
  leadTimePromedioMinutos: number | null;
  estadosActivos: Record<string, number>;
  ventasPorHora: VentaHora[];
  ultimoEventoEn: string | null;
  generadoEn: string;
}

export interface TopProducto {
  productoId: number;
  nombre: string;
  unidades: number;
  monto: number;
}

export interface RegistroAuditoria {
  eventoId: string;
  fuente: string;
  ocurridoEn: string;
  accion: string;
  entidad: string;
  entidadId: string;
  detalle?: string;
  actorId?: string;
  actorNombre?: string;
  actorRoles?: string;
  ip?: string;
}

export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

/** Mensaje legible a partir de un error HTTP (los microservicios responden ProblemDetail: title + detail). */
export function mensajeError(err: { status?: number; error?: { title?: string; detail?: string } }, accion: string): string {
  if (err.status === 0) return `${accion}: no hay conexión con el servidor`;
  if (err.status === 401) return `${accion}: la sesión expiró, vuelve a iniciar sesión`;
  if (err.status === 403) return `${accion}: tu rol no tiene permiso`;
  const detalle = err.error?.detail || err.error?.title;
  return detalle ? `${accion}: ${detalle}` : `${accion} (HTTP ${err.status})`;
}

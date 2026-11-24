import { Routes } from '@angular/router';
import { authGuard, rolGuard } from './core/guards';
import { LoginPage } from './pages/login';

/**
 * Rutas del caso Pedidos360. Los guards ocultan lo que el rol no puede usar; el backend (BFF y cada
 * microservicio) vuelve a validar el JWT y el rol en cada petición.
 * Cada pantalla se carga bajo demanda (loadComponent): el primer ingreso descarga menos código.
 */
export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'Pedidos360 · Ingreso' },
  // redirectUri de MSAL: handleRedirectPromise (app.config) procesa la respuesta y aquí se continúa al dashboard
  { path: 'auth/callback', redirectTo: 'dashboard' },
  {
    path: 'dashboard', canActivate: [authGuard], title: 'Pedidos360 · Inicio',
    loadComponent: () => import('./pages/dashboard').then((m) => m.DashboardPage),
  },
  {
    path: 'orders', canActivate: [authGuard], title: 'Pedidos360 · Pedidos',
    loadComponent: () => import('./pages/orders').then((m) => m.OrdersPage),
  },
  {
    path: 'orders/:id', canActivate: [authGuard], title: 'Pedidos360 · Pedido',
    loadComponent: () => import('./pages/order-detail').then((m) => m.OrderDetailPage),
  },
  {
    path: 'catalog', canActivate: [rolGuard('Admin', 'Operador')], title: 'Pedidos360 · Catálogo',
    loadComponent: () => import('./pages/catalog').then((m) => m.CatalogPage),
  },
  {
    path: 'reports', canActivate: [rolGuard('Admin')], title: 'Pedidos360 · Reportes',
    loadComponent: () => import('./pages/reports').then((m) => m.ReportsPage),
  },
  {
    path: 'audit', canActivate: [rolGuard('Admin', 'Auditor')], title: 'Pedidos360 · Auditoría',
    loadComponent: () => import('./pages/audit').then((m) => m.AuditPage),
  },
  {
    path: 'colas', canActivate: [rolGuard('Admin')], title: 'Pedidos360 · RabbitMQ',
    loadComponent: () => import('./estado-colas').then((m) => m.EstadoColas),
  },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'dashboard' },
];

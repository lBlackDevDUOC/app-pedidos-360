import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Rol } from './auth.service';

/** Ruta protegida: sin sesión de Microsoft → /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaLogueado() ? true : inject(Router).createUrlTree(['/login']);
};

/**
 * Ruta protegida por rol: además de la sesión, el usuario debe tener alguno de los roles indicados.
 * Uso en app.routes.ts: canActivate: [rolGuard('Admin', 'Operador')]
 */
export function rolGuard(...roles: Rol[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.estaLogueado()) {
      return router.createUrlTree(['/login']);
    }
    return auth.tieneRol(...roles) ? true : router.createUrlTree(['/dashboard'], { queryParams: { sinPermiso: 1 } });
  };
}

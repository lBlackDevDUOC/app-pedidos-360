import { Injectable, inject } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

/** App Roles definidos en el App Registration de Entra ID (claim "roles" del token). */
export type Rol = 'Admin' | 'Operador' | 'Cliente' | 'Auditor';

/**
 * Envoltorio de MSAL para el resto de la app: quién está logueado y qué roles tiene.
 * Los roles se leen del id_token (Entra ID los incluye cuando el usuario tiene App Roles asignados).
 * Sin rol asignado se considera Cliente, igual que en el backend.
 * Ocultar un botón aquí es solo comodidad visual: la seguridad real la aplican el BFF y cada microservicio.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private msal = inject(MsalService);

  estaLogueado(): boolean {
    return this.msal.instance.getAllAccounts().length > 0;
  }

  nombre(): string {
    const cuenta = this.msal.instance.getAllAccounts()[0];
    return cuenta?.name || cuenta?.username || '';
  }

  roles(): Rol[] {
    const cuenta = this.msal.instance.getAllAccounts()[0];
    const roles = (cuenta?.idTokenClaims?.['roles'] as Rol[] | undefined) ?? [];
    return roles.length ? roles : ['Cliente'];
  }

  tieneRol(...roles: Rol[]): boolean {
    const propios = this.roles();
    return roles.some((r) => propios.includes(r));
  }

  login(): void {
    this.msal.loginRedirect();
  }

  logout(): void {
    this.msal.logoutRedirect();
  }
}

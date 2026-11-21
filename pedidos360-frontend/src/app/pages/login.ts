import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

/**
 * /login (público). El inicio de sesión es OAuth 2.0 + OpenID Connect contra Microsoft Entra ID (flujo
 * Authorization Code con PKCE que implementa MSAL): la contraseña nunca pasa por esta app.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <section class="login">
      <h1>Pedidos360</h1>
      <p>Plataforma de pedidos y despacho para la red de panaderías y cafés.</p>
      <button type="button" class="btn primario" (click)="auth.login()">Iniciar sesión con Microsoft</button>
      <p class="nota">Se usa tu cuenta del tenant de Pedidos360 (Microsoft Entra ID).</p>
    </section>
  `,
})
export class LoginPage implements OnInit {
  auth = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    if (this.auth.estaLogueado()) {
      this.router.navigateByUrl('/dashboard');
    }
  }
}

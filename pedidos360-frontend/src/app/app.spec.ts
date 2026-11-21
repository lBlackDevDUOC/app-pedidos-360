import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { App } from './app';
import { AuthService } from './core/auth.service';

function msalCon(cuentas: unknown[]) {
  return { instance: { getAllAccounts: () => cuentas }, loginRedirect: () => {}, logoutRedirect: () => {} };
}

describe('App', () => {
  it('muestra el título y no muestra el menú sin sesión', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: MsalService, useValue: msalCon([]) }],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.querySelector('h1')?.textContent).toContain('Pedidos360');
    expect(html.querySelector('nav')).toBeNull();
  });

  it('el menú depende del rol del token', async () => {
    const operador = { name: 'Olga', idTokenClaims: { roles: ['Operador'] } };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: MsalService, useValue: msalCon([operador]) }],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const menu = (fixture.nativeElement as HTMLElement).querySelector('nav')?.textContent ?? '';
    expect(menu).toContain('Catálogo');
    expect(menu).not.toContain('Reportes');
  });
});

describe('AuthService', () => {
  it('sin roles en el token se considera Cliente', () => {
    TestBed.configureTestingModule({ providers: [{ provide: MsalService, useValue: msalCon([{ idTokenClaims: {} }]) }] });
    const auth = TestBed.inject(AuthService);
    expect(auth.roles()).toEqual(['Cliente']);
    expect(auth.tieneRol('Admin')).toBe(false);
  });
});

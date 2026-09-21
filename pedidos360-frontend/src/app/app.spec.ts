import { TestBed } from '@angular/core/testing';
import { MsalService } from '@azure/msal-angular';
import { App } from './app';

describe('App', () => {
  const msalMock = {
    instance: {
      getAllAccounts: () => [],
      handleRedirectPromise: () => Promise.resolve(null),
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: MsalService, useValue: msalMock }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Pedidos360');
  });
});

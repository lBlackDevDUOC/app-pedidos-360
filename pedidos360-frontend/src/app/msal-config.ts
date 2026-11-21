import {
  PublicClientApplication,
  IPublicClientApplication,
  InteractionType,
} from '@azure/msal-browser';
import { MsalInterceptorConfiguration } from '@azure/msal-angular';
import { environment } from '../environments/environment';

export function msalInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.azure.clientId,
      authority: environment.azure.authority,
      redirectUri: environment.azure.redirectUri,
    },
    cache: {
      cacheLocation: 'localStorage',
    },
  });
}

export function msalInterceptorConfigFactory(): MsalInterceptorConfiguration {
  // Todas las llamadas pasan por un solo origen (API Gateway en producción, ms-bff en local):
  // MSAL adjunta el access token (scope OT.Create) solo a esa URL.
  const protectedResourceMap = new Map<string, Array<string>>([
    [`${environment.apiBaseUrl}/*`, environment.azure.protectedResourceScopes],
  ]);

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap,
  };
}

import {PublicClientApplication, IPublicClientApplication, InteractionType} from '@azure/msal-browser';
import {MsalInterceptorConfiguration} from '@azure/msal-angular';
import {environment} from '../environments/environment';

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
  const protectedResourceMap = new Map<string, Array<string>>([
    [`${environment.apiBaseUrl}/`, environment.azure.protectedResourceScopes]
  ]);

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap
  };
}

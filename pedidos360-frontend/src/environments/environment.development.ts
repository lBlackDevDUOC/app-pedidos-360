// Entorno local (ng serve): microservicios corriendo en esta máquina (docker compose o IntelliJ).
// La app de Microsoft Entra es la misma que en producción.
export const environment = {
  production: false,
  azure: {
    clientId: 'd4b53b0b-a9b3-42b4-b63d-71d24f58813b',
    tenantId: 'bb5324af-c266-41ed-b36c-a971641c7af2',
    authority: 'https://login.microsoftonline.com/bb5324af-c266-41ed-b36c-a971641c7af2/v2.0',
    redirectUri: window.location.origin,
    protectedResourceScopes: ['api://d4b53b0b-a9b3-42b4-b63d-71d24f58813b/OT.Create'],
  },
  // ms-bff: única puerta de entrada (enruta a pedidos, catálogo, reportes, auditoría y administración)
  apiBaseUrl: 'http://localhost:8088',
};

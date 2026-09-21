export const environment = {
  production: true,
  azure: {
    clientId: 'd4b53b0b-a9b3-42b4-b63d-71d24f58813b',
    tenantId: 'bb5324af-c266-41ed-b36c-a971641c7af2',
    authority: 'https://login.microsoftonline.com/bb5324af-c266-41ed-b36c-a971641c7af2',
    redirectUri: 'http://98.84.170.100',
    protectedResourceScopes: ['api://d4b53b0b-a9b3-42b4-b63d-71d24f58813b/OT.Create'],
  },
  apiBaseUrl: 'http://98.84.170.100:8080',
};

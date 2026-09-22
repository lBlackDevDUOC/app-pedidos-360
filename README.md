# Pedidos360 — Evaluación Parcial N°1 (DSY1107)

Sistema de pedidos con frontend en Angular (MSAL / Azure AD) y backend en Spring Boot,
desplegado en AWS EC2 y protegido mediante AWS API Gateway.

## Índice

- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Cómo correr el proyecto localmente](#cómo-correr-el-proyecto-localmente)
- [Seguridad: autenticación y autorización](#seguridad-autenticación-y-autorización)
- [Despliegue en AWS](#despliegue-en-aws)
- [Evidencia de cumplimiento — Evaluación Parcial N°1](#evidencia-de-cumplimiento--evaluación-parcial-n1)

## Arquitectura

```
┌──────────────────┐        ┌──────────────────────┐        ┌───────────────────────────┐
│  Angular (MSAL)   │        │  Microsoft Entra ID   │        │      AWS API Gateway       │
│  localhost:4200   │───1──▶│  (IDaaS / Azure AD)   │        │  (HTTP API + JWT           │
│                   │◀──2───│  emite el JWT          │        │   Authorizer)               │
└─────────┬─────────┘        └──────────────────────┘        └─────────────┬──────────────┘
          │                                                                  │
          │  3. Request + Authorization: Bearer <JWT>                       │
          └─────────────────────────────────────────────────────────────────▶
                                                                              │
                                                        4. Valida issuer/audience/firma
                                                           (defensa en profundidad #1)
                                                                              │
                                                                              ▼
                                                                ┌───────────────────────────┐
                                                                │   EC2 — Docker             │
                                                                │  ┌───────────────────────┐ │
                                                                │  │ pedidos360-frontend    │ │
                                                                │  │ (Nginx, build Angular) │ │
                                                                │  └───────────────────────┘ │
                                                                │  ┌───────────────────────┐ │
                                                                │  │ pedidos360-backend     │ │
                                                                │  │ Spring Boot            │ │
                                                                │  │ 5. Vuelve a validar el │ │
                                                                │  │    JWT (issuer,        │ │
                                                                │  │    audience, firma,    │ │
                                                                │  │    scope) — defensa #2 │ │
                                                                │  └──────────┬────────────┘ │
                                                                │             │              │
                                                                │  ┌──────────▼────────────┐ │
                                                                │  │ pedidos360-db          │ │
                                                                │  │ PostgreSQL             │ │
                                                                │  └───────────────────────┘ │
                                                                └───────────────────────────┘
```

**Flujo resumido:**

1. El usuario inicia sesión en Angular vía `MsalService` (redirect a Microsoft).
2. Microsoft Entra ID valida credenciales y emite un `id_token` + `access_token` (scope `api://<clientId>/OT.Create`).
3. El `MsalInterceptor` adjunta el `access_token` como header `Authorization: Bearer ...` en cada llamada a la API.
4. **AWS API Gateway** (HTTP API) recibe la petición, valida el JWT con un **JWT Authorizer** propio (issuer + audience de Entra ID) antes de reenviarla al backend.
5. **Spring Boot** (OAuth2 Resource Server) vuelve a validar el mismo JWT de forma independiente (issuer, audience, firma, expiración) y aplica autorización por scope con `@PreAuthorize`.

Esta doble validación (Gateway + Backend) es intencional: cumple tanto el rol de "API Manager" como el de "BFF" que exige la rúbrica.

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Frontend | Angular 22 + `@azure/msal-angular` |
| Backend | Spring Boot 3 (Java), Spring Security OAuth2 Resource Server |
| Base de datos | PostgreSQL 16 (contenedor Docker) |
| IDaaS | Microsoft Entra ID (Azure AD) |
| Gateway | AWS API Gateway (HTTP API) con JWT Authorizer |
| Hosting | AWS EC2 (Docker + Docker Compose manual vía SSH) |
| CI/CD | GitHub Actions (`.github/workflows/deploy.yml`) |

## Estructura del repositorio

```
.
├── pedidos360-frontend/   # Angular + MSAL
├── pedidos360-backend/    # Spring Boot (API REST + seguridad JWT)
└── .github/workflows/     # Pipeline de despliegue a EC2
```

## Cómo correr el proyecto localmente

**Backend** (requiere `DB_USERNAME` / `DB_PASSWORD` y Postgres corriendo):

```bash
cd pedidos360-backend
./mvnw spring-boot:run
```

**Frontend:**

```bash
cd pedidos360-frontend
npm install
ng serve
```

Luego abrir `http://localhost:4200`.

## Seguridad: autenticación y autorización

- **Frontend**: `msal-config.ts` configura `MsalInterceptor` con un `protectedResourceMap` que adjunta el token solo a las rutas de la API (Gateway/EC2), pidiendo el scope `api://<clientId>/OT.Create`.
- **Azure AD**: la app expone su propia API (App ID URI `api://<clientId>`) con el scope delegado `OT.Create`, consentido en **API permissions**.
- **API Gateway**: `EntraID-Authorizer` (tipo JWT) valida `issuer` y `audience` antes de reenviar al backend. Las rutas `GET /api/health` y `OPTIONS *` quedan públicas (health check y preflight CORS respectivamente); todo lo demás bajo `/api/*` exige JWT válido.
- **Backend** (`SecurityConfig.java`): `oauth2ResourceServer().jwt()` valida issuer, audience, firma y expiración contra el JWKS de Entra ID. `@PreAuthorize("hasAuthority('SCOPE_OT.Create')")` protege la creación de pedidos por scope.

## Despliegue en AWS

El pipeline (`deploy.yml`) en cada push a `BlackDev`:
1. Compila el frontend Angular.
2. Sincroniza el repo en la EC2 vía SSH.
3. Reconstruye y levanta los contenedores Docker (`pedidos360-db`, `pedidos360-backend`, `pedidos360-frontend`) en una red Docker compartida.

El tráfico externo entra por **AWS API Gateway**, que reenvía a la EC2 (puerto 8080 para la API, 80 para el frontend estático si aplica).

---

## Evidencia de cumplimiento — Evaluación Parcial N°1

### A. Indicador 1 — Integración MSAL

**A1 — Login redirige a Microsoft Entra ID (federado con DuocUC):**

![Selección de cuenta Microsoft](docs/screenshots/a1-login-microsoft.png)
![Login federado DuocUC](docs/screenshots/a1b-login-duocuc-password.png)

**A2 — App logueada, mostrando datos del usuario desde el token:**

![App logueada](docs/screenshots/a2-app-logueada.png)

**A3 — Petición exitosa al endpoint `/token` de Microsoft (200 OK):**

![Token request 200](docs/screenshots/a3-token-request.png)

**A4 — Claims del `access_token` decodificado (`scp: OT.Create`, `aud`, `iss`):**

![JWT claims](docs/screenshots/a4-jwt-claims.png)

**A5 — `MsalInterceptor` adjuntando el header `Authorization: Bearer ...`:**

![Header Authorization](docs/screenshots/a5-authorization-header.png)

**A6 — Logout funcionando (vuelve a la pantalla de login):**

![Logout](docs/screenshots/a6-logout.png)

### B. Indicador 2 — Validación de JWT en el BFF

**B1 — Configuración de validación en el backend (`SecurityConfig.java` + `application.properties`):**

![SecurityConfig](docs/screenshots/b1-security-config.png)
![application.properties](docs/screenshots/b1b-application-properties.png)

**B2 — Rechazo sin token (401 Unauthorized) probado vía API Gateway:**

![401 sin token](docs/screenshots/b2-401-sin-token.png)

**B3 — Aceptación con token válido (200 OK) vía API Gateway:**

![200 con token](docs/screenshots/b3-200-con-token.png)

**B4 — Autorización por scope con `@PreAuthorize("hasAuthority('SCOPE_OT.Create')")`:**

![PreAuthorize scope](docs/screenshots/b4-preauthorize-scope.png)

**B5 — Manejo de errores correcto (`WWW-Authenticate` con detalle del error):**

![WWW-Authenticate error](docs/screenshots/b5-error-www-authenticate.png)

### C. Configuración de servicios en la nube

**C1 — Azure Entra ID, "Expose an API" con el scope `OT.Create`:**

![Expose an API](docs/screenshots/c1-entra-expose-api.png)

**C2 — Azure Entra ID, "API permissions" concedidos:**

![API permissions](docs/screenshots/c2-entra-api-permissions.png)

**C3 — Instancia EC2 corriendo en AWS:**

![EC2 running](docs/screenshots/c3-ec2-running.png)
![EC2 detalle](docs/screenshots/c3b-ec2-detalle.png)

**C4 — Contenedores Docker activos en la EC2 (`docker ps`):**

![docker ps](docs/screenshots/c4-docker-ps.png)

**C5 — AWS API Gateway, rutas configuradas (por método, sin `ANY`, para no bloquear el preflight CORS):**

![Gateway integraciones por ruta](docs/screenshots/ev2-cors-2-integracion.png)

**C6 — AWS API Gateway, `EntraID-Authorizer` (JWT Auth) asociado a cada ruta protegida:**

![Gateway authorizer por ruta](docs/screenshots/ev2-cors-1-authorizer.png)

**C7 — Prueba end-to-end vía Gateway (`/api/health` público, `/api/pedidos` protegido):**

![curl health](docs/screenshots/c7-gateway-curl-health.png)
![curl pedidos 401](docs/screenshots/b2-401-sin-token.png)

**C8 — Pipeline CI/CD exitoso en GitHub Actions:**

![GitHub Actions](docs/screenshots/c8-github-actions.png)

---

## Evidencia de cumplimiento — Evaluación Parcial N°2

### CORS configurado en el API Manager (AWS API Gateway)

La rúbrica de la EV2 exige que el **API Manager** (no el backend) resuelva CORS. Las rutas se
separaron por método (`GET`, `POST`, `PUT`, `DELETE`, todas con el `EntraID-Authorizer`) sin
usar `ANY`, para que el `OPTIONS` de preflight no quede capturado por ninguna ruta protegida y
el manejo automático de CORS de API Gateway lo resuelva directamente — sin pasar por el
authorizer ni por el backend.

**Rutas + integración por método:**

![Integraciones por ruta](docs/screenshots/ev2-cors-2-integracion.png)

**Authorizer JWT adjunto a cada ruta protegida:**

![Authorizer por ruta](docs/screenshots/ev2-cors-1-authorizer.png)

**Configuración de CORS en el Gateway (origin, headers, methods):**

![CORS config](docs/screenshots/ev2-cors-config.png)

**Prueba del preflight `OPTIONS` resuelto por el Gateway (204, sin pasar por el authorizer):**

![CORS curl test](docs/screenshots/ev2-cors-curl-test.png)

### Authorization Code + PKCE

MSAL.js usa por defecto el flujo Authorization Code con PKCE para SPAs (no Implicit). Evidencia:

**Request a `/authorize` con `code_challenge` y `code_challenge_method=S256`:**

![Authorize con PKCE](docs/screenshots/ev2-pkce-1-authorize-url.png)

**Canje del código por el token, con `code_verifier` y `grant_type=authorization_code`:**

![Token payload con code_verifier](docs/screenshots/ev2-pkce-2-token-payload.png)

### Validación JWT en las rutas — respuestas 200 / 401 / 403 coherentes

**`GET /api/pedidos/1` sin token → 401:**

![401 sin token](docs/screenshots/ev2-ruta-pedidos-id-401.png)

**`GET /api/pedidos/1` con token válido → 200 con el JSON esperado:**

![200 con token](docs/screenshots/ev2-ruta-pedidos-id-200.png)

**`POST /api/pedidos` autenticado pero con un token sin el scope `OT.Create` (se usó el `id_token` en vez del `access_token`) → 403 Forbidden:**

![403 sin scope](docs/screenshots/ev2-ruta-pedidos-post-403.png)

Esto demuestra la diferencia entre **autenticación** (401 si no hay token válido) y **autorización**
(403 si el token es válido pero no tiene el permiso/scope requerido) — ambas resueltas en el
Gateway y reforzadas otra vez en el backend.

**`GET /api/pedidos` (listado) con token → 200:** ver captura `b3-200-con-token.png` en la sección de la EV1 (mismo endpoint, misma evidencia, ya capturada).

**`POST /api/pedidos` con token válido → 201 Created, con el pedido creado en la respuesta:**

![201 headers](docs/screenshots/ev2-ruta-pedidos-post-201-headers.png)
![201 body](docs/screenshots/ev2-ruta-pedidos-post-201-body.png)

**`GET /api/clientes/me` con token válido → 200 con el perfil del cliente:**

![me headers](docs/screenshots/ev2-ruta-clientes-me-200-headers.png)
![me body](docs/screenshots/ev2-ruta-clientes-me-200-body.png)

Tenant IDaaS y app registrada: ver secciones **C1, C2, C3** de la evidencia de la EV1 más
arriba en este mismo README (mismo tenant, misma app, reutilizado directamente).

---

_Repositorio entregado como parte de la Evaluación Parcial N°1 y N°2 — DSY1107 Desarrollo Cloud Native I._

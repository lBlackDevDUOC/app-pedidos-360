# Pedidos360 — DSY1107 Desarrollo Cloud Native I

Sistema de pedidos con frontend en Angular (MSAL / Azure AD) y backend de microservicios en Spring Boot,
desplegado en AWS EC2 y protegido mediante AWS API Gateway. Desde la EP3 los microservicios se comunican
de forma asíncrona con **RabbitMQ** → ver [Evaluación Parcial N°3](#evaluación-parcial-n3--mensajería-asíncrona-con-rabbitmq).

## Índice

- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Cómo correr el proyecto localmente](#cómo-correr-el-proyecto-localmente)
- [Seguridad: autenticación y autorización](#seguridad-autenticación-y-autorización)
- [Despliegue en AWS](#despliegue-en-aws)
- [Evidencia de cumplimiento — Evaluación Parcial N°1](#evidencia-de-cumplimiento--evaluación-parcial-n1)
- [Evaluación Parcial N°3 — Mensajería asíncrona con RabbitMQ](#evaluación-parcial-n3--mensajería-asíncrona-con-rabbitmq)
- [EP5 / EFT — Streaming con Kafka, roles, catálogo y BFF](#ep5--eft--streaming-con-kafka-roles-catálogo-y-bff)
- [Evidencia de cumplimiento — EP5 / EP6 / EFT](#evidencia-de-cumplimiento--ep5--ep6--eft)

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
├── pedidos360-frontend/        # Angular + MSAL
├── pedidos360-backend/         # ms-pedidos: API REST + JWT + PostgreSQL; productor/consumidor RabbitMQ
├── pedidos360-documentos/      # ms-documentos: emite comprobantes (consumidor/productor)
├── pedidos360-notificaciones/  # ms-notificaciones: emails al cliente (consumidor)
├── pedidos360-rabbit-admin/    # ms-rabbit-admin: API REST para administrar RabbitMQ
├── rabbitmq/                   # policies de retención aplicadas al levantar el broker
├── docker-compose.yml          # RabbitMQ + PostgreSQL (+ los 4 microservicios con --profile app)
└── .github/workflows/          # Pipeline de despliegue a EC2
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

## Evaluación Parcial N°3 — Mensajería asíncrona con RabbitMQ

Al crear un pedido, la emisión del comprobante y el envío de emails ya no ocurren dentro de la petición HTTP:
`ms-pedidos` guarda el pedido, publica un evento y responde de inmediato. Otros microservicios procesan el
evento en segundo plano. Si uno de ellos está caído, el mensaje espera en su cola; si falla, se reintenta y,
si no se puede procesar, queda en una DLQ para revisión. La lógica existente (login, JWT, pedidos) no cambió.

### Flujo de mensajes

```
                         POST /api/pedidos (JWT)
 Angular ───────────────────────────────▶ ms-pedidos ──guarda──▶ PostgreSQL
                                             │
                                  publica "pedido.creado"
                                             ▼
                        ┌──────── [pedidos360.events] (topic) ────────┐
                        ▼                                              ▼
           documentos.pedido-creado.q                  notificaciones.pedido-creado.q
                        │                                              │
                  ms-documentos                                 ms-notificaciones
            emite comprobante BOL-000…                      email "recibimos tu pedido"
                        │
             publica "documento.generado"
                        ▼
                        ┌──────── [pedidos360.events] (topic) ────────┐
                        ▼                                              ▼
           pedidos.documento-generado.q              notificaciones.documento-generado.q
                        │                                              │
                   ms-pedidos                                   ms-notificaciones
          guarda N° de comprobante en el pedido          email "tu comprobante está listo"

 Cada cola de trabajo tiene:
   error recuperable  → [pedidos360.retry] → <cola>.retry (espera TTL) → vuelve a la cola
   error definitivo   → [pedidos360.dlx]   → <cola>.dlq   (cuarentena, alerta en ms-rabbit-admin)
```

### Microservicios

| Microservicio | Puerto | Rol en RabbitMQ | Seguridad |
|---|---|---|---|
| `ms-pedidos` (`pedidos360-backend`) | 8080 | Produce `pedido.creado`; consume `documento.generado` | JWT Entra ID + scope `OT.Create` |
| `ms-documentos` | 8081 | Consume `pedido.creado`; produce `documento.generado` | Sin API de negocio (solo `/actuator/health`) |
| `ms-notificaciones` | 8082 | Consume `pedido.creado` y `documento.generado` | Sin API de negocio (solo `/actuator/health`) |
| `ms-rabbit-admin` | 8083 | Crea/elimina colas, exchanges y bindings; monitorea DLQ | JWT Entra ID |

Cada microservicio organiza la mensajería en paquetes separados del negocio:

```
messaging/
├── config/      RabbitProperties (nombres desde application.yml), RabbitTopologyConfig (beans), RabbitInfraConfig
├── event/       contratos JSON de los eventos
├── producer/    interfaz XxxEventPublisher + implementación RabbitMQ
├── consumer/    @RabbitListener agrupados por dominio: consumer/pedidos, consumer/documentos
└── support/     MensajeLector (JSON + validación) y ReintentoHandler (reintento / DLQ)
service/         lógica de negocio: no importa nada de RabbitMQ (verificado con ArchUnit)
```

### Configuración centralizada

Todos los nombres de exchanges, colas y routing keys están **solo** en el bloque `app.rabbitmq` del
`application.yml` de cada microservicio y se leen con `@ConfigurationProperties` (`RabbitProperties`).
Los `@RabbitListener` usan placeholders (`${app.rabbitmq.colas.pedido-creado.nombre}`). El test
`ConfiguracionCentralizadaTest` recorre el código fuente y falla si encuentra un nombre escrito a mano.

### Confirmación de mensajes (ACK manual) y manejo de errores

`acknowledge-mode: manual` en todos los consumidores. Cada `@RabbitListener` decide explícitamente:

| Situación | Ejemplo | Decisión | Resultado |
|---|---|---|---|
| Procesado correctamente | comprobante emitido | `basicAck` | RabbitMQ elimina el mensaje |
| Error **no recuperable** | JSON corrupto, campos obligatorios faltantes, pedido inexistente, email inválido | `basicNack(requeue=false)` | DLX → `.dlq` sin reintentar (reintentar daría el mismo error) |
| Error **recuperable** | BD caída, servicio externo con timeout, broker sin conexión al publicar | `ReintentoHandler`: copia a `.retry` con header `x-reintentos` + `basicAck` | Vuelve a la cola tras el TTL (5 s); máx. 3 reintentos |
| Reintentos agotados | el servicio sigue caído | `basicNack(requeue=false)` | DLX → `.dlq` |
| El consumidor muere a mitad del proceso | se detiene el contenedor | (sin ACK) | RabbitMQ re-entrega el mensaje: no se pierde |

- Nunca se usa `requeue=true`: re-encolar al instante genera un bucle que satura al consumidor.
- Entrega "al menos una vez" → los consumidores son **idempotentes** (el N° de comprobante se deriva del id del pedido; `registrarComprobante` ignora duplicados).
- Productores: mensajes **persistentes** + colas/exchanges **durables**; *publisher confirms* y *returns* registran en el log si el broker rechaza un mensaje o si no tiene ruta.
- `prefetch` (5 / 10) limita los mensajes sin confirmar por consumidor (reparto justo, sin saturar memoria).
- Cada decisión queda en el log con etiquetas `[ACK]`, `[REINTENTO n/3]`, `[NACK->DLQ]`, `[DLQ]`.

### Retención y limpieza

| Dónde | Límite | Efecto |
|---|---|---|
| Colas de trabajo (`x-message-ttl`) | 10 min | Un mensaje que nadie procesa expira y pasa a la DLQ |
| Colas de trabajo (`x-max-length` + `drop-head`) | 1.000 mensajes | Al desbordar se descartan los más antiguos hacia la DLQ |
| DLQ (`x-message-ttl`, `x-max-length`) | 24 h / 5.000 mensajes | La cuarentena se limpia sola |
| Colas `.retry` (`x-message-ttl`) | 5 s | Espera entre reintentos |
| Policy `retencion-global` (broker) | 100.000 mensajes | Red de seguridad para cualquier cola (ej. creadas por la API admin) |
| Policy `retencion-dlq` (broker) | 7 días / 10.000 mensajes | Red de seguridad para cualquier `*.dlq` |

Las policies se aplican automáticamente con `rabbitmq/aplicar-policies.sh` al levantar `docker compose`.
`ms-rabbit-admin` revisa las DLQ cada 30 s y deja un `[ALERTA-DLQ]` en el log si tienen mensajes
(también en `GET /api/admin/alerts` y en el panel de Angular).

### API del microservicio administrador (`ms-rabbit-admin`)

Swagger UI: `http://localhost:8083/swagger-ui.html` (botón **Authorize** → pegar el access token de Microsoft).

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/api/admin/queues` | 200 — colas con mensajes listos / sin confirmar / consumidores |
| `GET` | `/api/admin/queues/{nombre}` | 200 / 404 |
| `POST` | `/api/admin/queues` | 201 + `Location` / 400 validación / 409 ya existe o protegida / 422 DLX inexistente |
| `DELETE` | `/api/admin/queues/{nombre}` | 204 / 404 / 409 protegida |
| `DELETE` | `/api/admin/queues/{nombre}/messages` | 200 — purga (vacía la cola sin eliminarla) |
| `GET` | `/api/admin/exchanges` | 200 |
| `POST` | `/api/admin/exchanges` | 201 / 400 (tipo distinto de DIRECT, TOPIC, FANOUT, HEADERS) / 409 |
| `DELETE` | `/api/admin/exchanges/{nombre}` | 204 / 404 / 409 |
| `GET` | `/api/admin/bindings` | 200 |
| `POST` | `/api/admin/bindings` | 201 / 404 cola o exchange inexistente / 409 |
| `DELETE` | `/api/admin/bindings?exchange=&cola=&routingKey=` | 204 / 404 |
| `GET` | `/api/admin/alerts` | 200 — DLQ con mensajes |

- Toda la lógica está en `RabbitAdminService` (escrituras con `AmqpAdmin`, lecturas con la API HTTP de Management); los controladores solo llaman a sus métodos.
- Validación con DTOs (`@NotBlank`, `@Pattern`, `@Positive`, `@Max`, `@AssertTrue`) y errores RFC 7807 con detalle por campo. No se aceptan nombres vacíos, con espacios, con el prefijo reservado `amq.`, TTL/largos ≤ 0, routing key de DLX sin exchange DLX, ni tipos de exchange inexistentes.
- Las colas y exchanges del sistema (prefijos `pedidos360.`, `pedidos.`, `documentos.`, `notificaciones.`) están protegidos: se pueden purgar pero no eliminar.
- Peticiones de ejemplo: [`docs/pruebas-rabbit-admin.http`](docs/pruebas-rabbit-admin.http).

### Cómo ejecutar

Requisitos: Docker Desktop, JDK 21, Node 22.

```bash
# 1) RabbitMQ + PostgreSQL + los 4 microservicios
docker compose --profile app up -d --build

# 2) Frontend (apunta a localhost:8080 y localhost:8083)
cd pedidos360-frontend && npm install && npx ng serve
```

- App: `http://localhost:4200` · RabbitMQ Management: `http://localhost:15672` (`pedidos360` / `pedidos360`)
- Para correr los microservicios desde IntelliJ: `docker compose up -d` (solo RabbitMQ y PostgreSQL) y ejecutar cada `*Application`.

**En AWS** se usan dos EC2 t3.micro en la misma VPC (ver [`docs/guia-aws-ep3.md`](docs/guia-aws-ep3.md)):
la EC2-1 mantiene frontend + `ms-pedidos` + PostgreSQL y la EC2-2 es el servidor de mensajería
(RabbitMQ + `ms-documentos` + `ms-notificaciones` + `ms-rabbit-admin`, `deploy/servidor-mensajeria/`).
`ms-pedidos` llega a RabbitMQ por IP privada y el puerto 5672 solo acepta conexiones desde el Security Group
de la EC2-1. El pipeline de GitHub Actions ejecuta los tests de los 4 microservicios antes de desplegar.

**Clúster RabbitMQ (EP4).** En la EC2-2 (t3.small) `docker compose` levanta **dos nodos** RabbitMQ
(`rabbit@pedidos360-rabbitmq-1` y `rabbit@pedidos360-rabbitmq-2`) que comparten la Erlang cookie y forman un clúster
automáticamente (`cluster_formation.peer_discovery_backend = classic_config`). Con `queue_leader_locator = balanced`
las colas se reparten entre ambos nodos, y los microservicios conocen los dos (`spring.rabbitmq.addresses`): si un nodo
cae, se reconectan al otro, y al volver el nodo sus colas y consumidores se recuperan solos. Las colas son *classic*
(cada una vive en un nodo); para que una cola sobreviva a la caída de su nodo, el paso siguiente sería un clúster de
3 nodos con *quorum queues*. Detalle y verificación: [`docs/guia-aws-ep3.md`](docs/guia-aws-ep3.md#ep4-clúster-rabbitmq-de-2-nodos).

**Demostración de fallos** (simulación en `ms-documentos`, `app.simulacion` en su `application.yml`):

| Total del pedido | Qué pasa |
|---|---|
| cualquier otro | Flujo normal: comprobante emitido y 2 emails |
| `7` | Falla el 1er intento, `[REINTENTO 1/3]` y se recupera |
| `13` | Falla siempre: 3 reintentos y termina en `documentos.pedido-creado.dlq` |
| `666` | Error no recuperable: va directo a la DLQ, sin reintentos |

### Pruebas automáticas

```bash
cd pedidos360-backend && mvn test        # 20 tests
cd pedidos360-documentos && mvn test     # 16 tests
cd pedidos360-notificaciones && mvn test # 15 tests
cd pedidos360-rabbit-admin && mvn test   # 39 tests
```

| Criterio de la rúbrica | Dónde está | Test que lo verifica |
|---|---|---|
| Nombres centralizados (12%) | `application.yml` → `RabbitProperties` | `ConfiguracionCentralizadaTest` (falla si hay nombres escritos a mano) |
| Beans Queue / Exchange / Binding (13%) | `RabbitTopologyConfig` de cada microservicio | `ConfiguracionCentralizadaTest`, `RabbitAdminServiceTest` |
| Configuración separada del negocio (10%) | paquete `messaging/` vs `service/` | `ArquitecturaTest` (ArchUnit) |
| `@RabbitListener` por dominio (15%) | `messaging/consumer/{pedidos,documentos}` | `ArquitecturaTest`, `*ListenerTest` |
| ACK y manejo de errores (20%) | `*Listener` + `ReintentoHandler` | `*ListenerTest`, `ReintentoHandlerTest` |
| API REST del administrador (13%) | `ColaController`, `ExchangeController`, `BindingController` | `AdminApiTest` |
| Lógica en `RabbitAdminService` (10%) | `RabbitAdminService` | `RabbitAdminServiceTest`, `ArquitecturaTest` |
| Validación de entrada (7%) | DTOs + `ErroresApiHandler` | `AdminApiTest` |

---

_Repositorio entregado como parte de las Evaluaciones Parciales N°1, N°2 y N°3 — DSY1107 Desarrollo Cloud Native I._

---

## EP5 / EFT — Streaming con Kafka, roles, catálogo y BFF

### Arquitectura final

```
Angular (MSAL) ─▶ Entra ID (App Roles: Admin, Operador, Cliente, Auditor)
     │ Bearer JWT
     ▼
AWS API Gateway (JWT Authorizer) ─▶ EC2-1 ms-bff :8080 (Spring Cloud Gateway: valida JWT + rol)
                                         ├─▶ ms-pedidos ──AMQP──▶ EC2-2 RabbitMQ ×2 (comandos: comprobante, correo)
                                         │        └─────Kafka───▶ EC2-3 orders.events / audit.timeline (hechos)
                                         ├─▶ ms-catalog (el stock baja al aceptar; token relay)
                                         ├─▶ EC2-3 ms-report      (consumer group report-service → KPIs)
                                         ├─▶ EC2-3 ms-audit       (consumer group audit-service  → timeline)
                                         ├─▶ EC2-3 ms-kafka-admin (tópicos, configs, consumer groups, lag)
                                         └─▶ EC2-2 ms-rabbit-admin
```

**RabbitMQ = comandos** (un consumidor los ejecuta y se borran). **Kafka = hechos** (quedan en un log; varios
servicios los leen, cada uno con su consumer group, y se pueden releer). Kafka se sumó sin tocar el flujo de RabbitMQ.

### Microservicios

| Servicio | Puerto | Responsabilidad | Exposición |
|---|---|---|---|
| ms-bff | 8088 (8080 en EC2-1) | Puerta de entrada, JWT, rol por ruta, CORS | API Gateway |
| ms-pedidos (`pedidos360-backend`) | 8080 | Pedidos, ciclo de vida (enum), productor RabbitMQ + Kafka | interno |
| ms-catalog | 8087 | Productos, precios, stock (descuento todo-o-nada e idempotente) | interno |
| ms-report | 8084 | KPIs: ventas por hora, lead time, estados activos, top productos | interno |
| ms-audit | 8085 | Timeline de auditoría (solo lectura) | interno |
| ms-kafka-admin | 8086 | Tópicos, configuraciones, consumer groups, offsets y lag | interno |
| ms-documentos / ms-notificaciones / ms-rabbit-admin | 8081-8083 | EP3/EP4 | interno |

### Kafka (`deploy/servidor-streaming`)

- 3 Zookeeper (quórum 2) + 3 brokers (Confluent 7.9 = Kafka 3.9) + Kafka-UI, `min.insync.replicas=2`,
  sin auto-creación de tópicos.
- Tópicos (`crear-topicos.sh`, idempotente):

| Tópico | Part. | RF | cleanup.policy | Retención | Key |
|---|---|---|---|---|---|
| orders.events | 3 | 3 | delete | 7 días | id del pedido (orden por pedido) |
| audit.timeline | 3 | 3 | compact,delete | 30 días | id del evento |
| orders.events.report.DLT, orders.events.audit.DLT, audit.timeline.audit.DLT | 3 | 3 | delete | 14 días | original |

- Productor: `acks=all`, idempotencia, lz4, JSON con fechas ISO, envío después del commit de la BD.
- Consumidores: `ErrorHandlingDeserializer`, validación de payload, reintentos con backoff exponencial y DLT con
  headers de diagnóstico, idempotencia por `eventoId`, rebalanceo cooperativo, 3 hilos por grupo.

Levantar en local (Kafka-UI en http://localhost:8090):

```bash
cd deploy/servidor-streaming && docker compose --profile init --profile app up -d --build
```

### Roles (App Roles de Entra ID)

| Rol | Puede |
|---|---|
| Cliente (o sin rol) | Crear pedidos con productos del catálogo, ver y cancelar los suyos |
| Operador | Ver todos los pedidos y moverlos de estado (aceptar descuenta stock), ver el catálogo |
| Admin | Catálogo (crear/editar), reportes, auditoría, administración de Kafka y RabbitMQ |
| Auditor | Auditoría (solo lectura) |

### Despliegue

Push a `BlackDev` → job `ci` (compila y prueba los 9 microservicios y el frontend). El job `deploy` no corre solo:
se ejecuta cuando el mensaje del commit lleva `[deploy]` o al lanzar el workflow a mano (Actions → Run workflow).
Pasos de AWS y Azure: [docs/guia-aws-ep5.md](docs/guia-aws-ep5.md).

## Evidencia de cumplimiento — EP5 / EP6 / EFT

Capturas tomadas sobre el sistema desplegado en AWS (3 EC2 en la misma VPC, un grupo de seguridad por máquina).
Se guardan en [`docs/evidencias-ep5/`](docs/evidencias-ep5).

### API Manager y validación del JWT

Una ruta protegida `/api/{proxy+}` hacia el BFF y una pública `/api/health`; el autorizador JWT valida emisor y
audiencia de Entra ID. El frontend llama solo a `execute-api`.

![Rutas del API Gateway](docs/evidencias-ep5/gw-1-rutas.png)
![Autorizador JWT](docs/evidencias-ep5/gw-2-autorizador-jwt.png)
![Llamadas del frontend al gateway](docs/evidencias-ep5/gw-3-network-execute-api.png)

Sin token → 401, token inválido → 401, token real → 200:

![Sin token](docs/evidencias-ep5/seg-1-sin-token-401.png)
![Token inválido](docs/evidencias-ep5/seg-2-token-invalido-401.png)
![Token válido](docs/evidencias-ep5/seg-3-token-valido-200.png)

### Identidad y roles

Cuatro App Roles en Entra ID; quien no tiene rol se trata como Cliente. El rol viaja en el claim `roles` del JWT y
define el menú de la aplicación y lo que aceptan el BFF y los microservicios.

![App roles](docs/evidencias-ep5/roles-1-app-roles.png)
![Usuarios y roles asignados](docs/evidencias-ep5/roles-2-usuarios-asignados.png)
![Claims del JWT](docs/evidencias-ep5/roles-3-jwt-claims.png)
![Menú Admin](docs/evidencias-ep5/menu-admin.png)
![Menú Operador](docs/evidencias-ep5/menu-operador.png)

### Kafka: arquitectura y tolerancia a fallos

3 ZooKeeper + 3 brokers, factor de replicación 3 y `min.insync.replicas=2`: 3 brokers en línea, 195 réplicas en
sincronía y 0 particiones sub-replicadas.

![Brokers en Kafka UI](docs/evidencias-ep5/kafka-1-brokers.png)
![Tópicos con 3 particiones y RF 3](docs/evidencias-ep5/kafka-2-topicos.png)
![describe de orders.events](docs/evidencias-ep5/kafka-3-describe-orders-events.png)
![describe de audit.timeline (compact,delete)](docs/evidencias-ep5/kafka-4-describe-audit-timeline.png)

Al detener el broker 2 el ISR baja de 3 a 2 y se elige otro líder; al volver, el ISR regresa a 3:

![Broker 2 detenido](docs/evidencias-ep5/kafka-6-broker-detenido.png)
![Broker 2 recuperado](docs/evidencias-ep5/kafka-7-broker-recuperado.png)

### Kafka: flujo de eventos, consumer groups y lag

Mensaje real de `orders.events` (key = id del pedido, JSON con `eventoId`, tipo y versión):

![Mensaje con key y JSON](docs/evidencias-ep5/kafka-5-mensaje-key-json.png)

Dos consumer groups independientes (`report-service`, `audit-service`), una partición por consumidor y lag 0. Con
`ms-report` detenido el grupo queda sin miembros activos y el lag sube; al reiniciarlo vuelve a 0 sin perder mensajes:

![Consumer groups](docs/evidencias-ep5/kafka-8-consumer-groups.png)
![Tres particiones, lag 0](docs/evidencias-ep5/kafka-9-lag-0.png)
![Lag mayor que 0 y recuperación](docs/evidencias-ep5/kafka-10-lag-y-recuperacion.png)

### Kafka: administración por API (`ms-kafka-admin`)

![Offsets y lag por partición](docs/evidencias-ep5/kafka-11-api-offsets-lag.png)
![Configuración inválida rechazada con 400](docs/evidencias-ep5/kafka-12-config-invalida-400.png)

### Aplicación: reaccionando a los eventos

![Reportería en tiempo real](docs/evidencias-ep5/app-1-reportes.png)
![Trazabilidad de un pedido (ms-audit)](docs/evidencias-ep5/app-2-trazabilidad.png)
![Estados y stock tras aceptar](docs/evidencias-ep5/app-3-estados-y-stock.png)

### RabbitMQ: clúster, colas y DLQ

![Clúster de 2 nodos](docs/evidencias-ep5/rabbit-1-nodos.png)
![12 colas (trabajo, .retry y .dlq)](docs/evidencias-ep5/rabbit-2-colas.png)
![Exchanges](docs/evidencias-ep5/rabbit-3-exchanges.png)

Un pedido de monto libre de 666 simula datos inválidos: ms-documentos lo rechaza (NACK) y queda en la DLQ; el panel
de administración muestra la alerta.

![DLQ con 1 mensaje](docs/evidencias-ep5/rabbit-4-dlq-ready-1.png)
![Log del NACK](docs/evidencias-ep5/rabbit-5-log-nack-dlq.png)
![Alerta en el panel](docs/evidencias-ep5/rabbit-6-panel-alerta-dlq.png)

### Despliegue

Tres EC2 en ejecución, Docker Compose con 11 contenedores sanos en la máquina de streaming y GitHub Actions
(`ci` + `deploy`). La EC2-1 llega a Kafka (puerto 9092) y la EC2-2 no: cada máquina tiene su rol.

![Instancias EC2](docs/evidencias-ep5/deploy-1-ec2.png)
![docker compose ps](docs/evidencias-ep5/deploy-2-compose-ps.png)
![Circuito cerrado EC2-1 / EC2-2](docs/evidencias-ep5/deploy-3-circuito-cerrado.png)
![Pipeline de GitHub Actions](docs/evidencias-ep5/deploy-4-github-actions.png)

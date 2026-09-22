# Capturas de evidencia

## A — MSAL (frontend)
- `a1-login-microsoft.png` — pantalla de login redirigiendo a Microsoft
- `a2-app-logueada.png` — app mostrando "¡Bienvenido, tu-correo!"
- `a3-token-request.png` — DevTools Network, POST a `oauth2/v2.0/token` (200)
- `a4-jwt-claims.png` — jwt.ms mostrando `scp`, `aud`, `iss` del access_token
- `a5-authorization-header.png` — DevTools Network, request con header `authorization: Bearer ...`
- `a6-logout.png` — logout funcionando

## B — Validación JWT en el backend
- `b1-security-config.png` — código de `SecurityConfig.java` / `application.properties`
- `b2-401-sin-token.png` — 401 al pedir `/api/pedidos` sin token
- `b3-200-con-token.png` — 200 con datos al pedir `/api/pedidos` con token
- `b4-preauthorize-scope.png` — código `@PreAuthorize` + POST exitoso (201)
- `b5-error-www-authenticate.png` — header `WWW-Authenticate` con detalle del error

## C — Infraestructura cloud
- `c1-entra-expose-api.png` — Azure Portal, "Expose an API" con scope `OT.Create`
- `c2-entra-api-permissions.png` — Azure Portal, "API permissions" concedidos
- `c3-ec2-running.png` — AWS Console, instancia EC2 en estado running
- `c4-docker-ps.png` — `docker ps` en la EC2 con los 3 contenedores
- `c5-gateway-routes.png` — AWS API Gateway, sección Routes
- `c6-gateway-authorizer.png` — AWS API Gateway, authorizer `EntraID-Authorizer`
- `c7-gateway-curl-test.png` — curl a `/api/health` (200) y `/api/pedidos` (401) vía Gateway
- `c8-github-actions.png` — GitHub Actions, run exitoso del deploy

No pasa nada si te faltan algunas o si sacas más de una por punto (ej. `a3-token-request-2.png`) —
avísame cuando subas las que tengas y yo edito el README para insertarlas donde corresponda.

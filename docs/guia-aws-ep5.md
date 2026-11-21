# Guía cloud para la entrega final (EP5 + EP6 + EFT)

Lo que hay que hacer **a mano** en Azure y AWS. Todo lo demás (código, docker-compose, pipeline) ya está en el repo.

Arquitectura final:

```
 Navegador ──login OIDC──▶ Microsoft Entra ID (App Roles: Admin, Operador, Cliente, Auditor)
     │
     │ Bearer JWT
     ▼
 AWS API Gateway (HTTP API, JWT Authorizer)  ── una sola ruta: ANY /api/{proxy+} ──┐
                                                                                   │
┌──────────────────────────── VPC (la misma de siempre) ───────────────────────────┼──────────────────┐
│ EC2-1 apps (sg-web)                    EC2-2 mensajería (sg-mq)                   ▼                  │
│  frontend :80                           RabbitMQ ×2 :5672/:5673 ◀── sg-web     ms-bff :8080        │
│  ms-bff :8080 ─┬─ ms-pedidos  (interno)  ms-documentos, ms-notificaciones         (en la EC2-1)      │
│                ├─ ms-catalog  (interno)  ms-rabbit-admin :8083 ◀── sg-web                            │
│                ├──────────────────────────────▶ EC2-3 :8084-8086 report/audit/kafka-admin            │
│                └──────────────────────────────▶ EC2-2 :8083 rabbit-admin                             │
│  ms-pedidos ──Kafka 9092-9094──▶ EC2-3 streaming (sg-kafka): Zookeeper ×3 (sin puertos),            │
│                                   Kafka ×3, Kafka-UI :8090 ◀── tu IP, postgres-analítica (sin puertos)│
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Parte A — Azure (Entra ID): App Roles

1. portal.azure.com → **Microsoft Entra ID** → **App registrations** → la app de Pedidos360 (Client ID `d4b53b0b-…`).
2. **App roles** → **Create app role**, cuatro veces:

| Display name | Value | Allowed member types | Description |
|---|---|---|---|
| Admin | `Admin` | Users/Groups | Administra catálogo, reportes y plataformas |
| Operador | `Operador` | Users/Groups | Gestiona pedidos (aceptar, preparar, despachar) |
| Cliente | `Cliente` | Users/Groups | Crea y sigue sus pedidos |
| Auditor | `Auditor` | Users/Groups | Lee la auditoría |

   El **Value** debe ser exactamente ese (el backend lo lee del claim `roles`).
3. **Enterprise applications** → la misma app → **Users and groups** → **Add user/group**: asigna un rol a cada usuario
   de prueba. Ideal: 4 usuarios (uno por rol) para la demo. Un usuario sin rol entra como Cliente.
4. Cierra sesión y vuelve a entrar en el frontend: el token nuevo trae `"roles": ["Operador"]`. Lo puedes ver
   pegando el token en jwt.ms (para la defensa).

## Parte B — AWS

### B1. EC2-1: subir de tamaño
Ahora corre frontend + BFF + ms-pedidos + ms-catalog + PostgreSQL. Con t3.micro (1 GB) no alcanza.
EC2 → la EC2-1 → **Detener** → Acciones → Configuración de instancia → **Cambiar tipo** → **t3.small** (o t3.medium) → Iniciar.
Si no tiene IP elástica, la IP pública cambia: actualiza el secret `EC2_HOST` y la integración del API Gateway.

### B2. EC2-3 (streaming)
1. **Security Group `pedidos360-streaming-sg`** (misma VPC que la EC2-1):

| Tipo | Puerto | Origen | Por qué |
|---|---|---|---|
| SSH | 22 | `0.0.0.0/0` | GitHub Actions (solo entra quien tiene la llave `.pem`) |
| TCP | 9092-9094 | **SG de la EC2-1** | Brokers Kafka: solo el productor (ms-pedidos) |
| TCP | 8084-8086 | **SG de la EC2-1** | report / audit / kafka-admin: solo los llama el BFF |
| TCP | 8090 | **Mi IP** | Kafka-UI (además tiene usuario y clave) |

   **No tienen regla** (decirlo en la defensa): 2181 Zookeeper y 5432 Postgres analítica. La EC2-2 no puede entrar aquí.
2. **Lanzar la instancia** `pedidos360-streaming`: Amazon Linux 2023, **t3.large**, par de claves `Pedidos360`, misma
   VPC y subred, IP pública habilitada, SG anterior, **30 GiB gp3**. No instales nada: el pipeline corre `preparar-ec2.sh`.
3. **IP elástica** → asociarla. Anota la IP elástica y la **IP privada**.

### B3. Ajustar los Security Groups que ya existen
- **SG de la EC2-2** (mensajería): el 8083 (rabbit-admin) que estaba en `0.0.0.0/0` → cambiar el origen a **SG de la EC2-1**.
  Ahora lo llama el BFF, no el API Gateway.
- **SG de la EC2-1**: dejar 80 y 8080 abiertos (frontend y BFF). El 5432 de PostgreSQL ya no se publica; si tenías una
  regla para 5432, elimínala.

### B4. Secrets nuevos en GitHub
Repositorio → Settings → Secrets and variables → Actions:

| Secret | Valor |
|---|---|
| `KAFKA_EC2_HOST` | IP elástica de la EC2-3 |
| `KAFKA_PRIVATE_IP` | IP privada de la EC2-3 |
| `KAFKA_UI_USER` / `KAFKA_UI_PASSWORD` | credenciales para Kafka-UI |
| `FRONTEND_ORIGIN` | URL del frontend, ej. `http://52.86.170.189` (para CORS del BFF; opcional si es esa) |

Se reutilizan: `EC2_HOST`, `MQ_EC2_HOST`, `MQ_PRIVATE_IP`, `EC2_USERNAME`, `EC2_SSH_KEY`, `DB_USERNAME`, `DB_PASSWORD`,
`RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_ERLANG_COOKIE`.

### B5. API Gateway: una sola ruta hacia el BFF
API Gateway → tu HTTP API → **Routes**:
- Deja (o crea) **`ANY /api/{proxy+}`** con integración HTTP `http://<IP EC2-1>:8080/api/{proxy}` y el **JWT Authorizer**.
- La ruta pública `GET /api/health` sin authorizer (health check).
- Las rutas viejas sueltas (`/api/pedidos`, `/api/admin/*`, …) se pueden borrar: el BFF enruta todo.
- CORS del API Gateway: origen del frontend, métodos GET/POST/PUT/DELETE/OPTIONS, headers Authorization y Content-Type.

### B6. Desplegar
Push a `BlackDev` = solo compila y prueba. Para desplegar: **Actions → Deploy Pedidos360 → Run workflow** con las 3
EC2 encendidas. Orden automático: EC2-3 (Kafka) → EC2-2 (RabbitMQ) → EC2-1 (apps).

### B7. Verificar
```bash
ssh -i Pedidos360.pem ec2-user@<IP_EC2_1> "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
```
```bash
ssh -i Pedidos360.pem ec2-user@<IP_EC2_3> "cd streaming && sudo docker compose --profile app ps"
```
- Kafka-UI: `http://<IP_EC2_3>:8090` → 3 brokers, tópicos con 3 particiones / RF 3, grupos `report-service` y `audit-service`.
- Circuito cerrado: en la EC2-1 y luego en la EC2-2 ejecuta
  `timeout 3 bash -c '</dev/tcp/<ip-privada-ec2-3>/9092' && echo ABIERTO || echo BLOQUEADO`
  → EC2-1 ABIERTO, EC2-2 BLOQUEADO.
- Frontend: login con cada usuario de prueba → el menú cambia según el rol.

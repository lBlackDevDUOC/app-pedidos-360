# Guía AWS: servidor de streaming Kafka (EC2-3) para la EP5

Arquitectura después de la EP5:

```
                          Internet
                             │
                    AWS API Gateway (JWT Authorizer Entra ID)
                             │
┌──────────────────────────── VPC (la misma de siempre) ───────────────────────────────┐
│ EC2-1 pedidos360 (sg-web)          EC2-2 mensajería (sg-mq)     EC2-3 streaming (sg-kafka)
│  frontend :80                       RabbitMQ ×2 :5672/:5673      Zookeeper ×3 (sin puertos)
│  ms-pedidos :8080 ──AMQP────────▶   ms-documentos                Kafka ×3 :9092/:9093/:9094 ◀── solo sg-web
│        │                            ms-notificaciones            Kafka-UI :8090 ◀── solo tu IP
│        └──────────Kafka (IP privada)──────────────────────────▶  ms-report :8084
│                                                                   ms-audit :8085
│                                                                   ms-kafka-admin :8086
│                                                                   postgres-analítica (sin puertos)
└──────────────────────────────────────────────────────────────────────────────────────┘
```

Tiempo estimado: 30–40 minutos.

---

## Paso 1: anotar los datos de la EC2-1

EC2 → Instancias → la EC2-1 → pestañas **Redes** y **Seguridad**. Anota el **ID de VPC**, el **ID de subred** y el
**Security Group** (`sg-...`). Es el mismo dato que usaste en la EP3 para RabbitMQ.

## Paso 2: crear el Security Group `pedidos360-streaming-sg`

EC2 → Grupos de seguridad → Crear. **VPC: la del paso 1.**

| Tipo | Puerto | Origen | Por qué |
|---|---|---|---|
| SSH | 22 | `0.0.0.0/0` | GitHub Actions despliega por SSH (entra solo quien tiene la llave `.pem`) |
| TCP personalizado | 9092-9094 | **SG de la EC2-1** | Los 3 brokers: solo el productor (ms-pedidos) |
| TCP personalizado | 8084-8086 | `0.0.0.0/0` por ahora* | APIs de report, audit y kafka-admin (las llama el API Gateway; exigen JWT) |
| TCP personalizado | 8090 | **Mi IP** | Kafka-UI, solo para ti (tiene además usuario y clave) |

\* Cuando exista el BFF (bloque 9) este origen pasa a ser **solo el SG de la EC2-1**.

**Lo que NO se abre** (y hay que decirlo en la defensa): 2181 (Zookeeper) y 5432 (Postgres analítica) no tienen regla.
Solo existen dentro de la red de Docker de la EC2-3. La EC2-2 no tiene ninguna regla aquí.

## Paso 3: lanzar la EC2-3

- Nombre: `pedidos360-streaming`
- AMI: **Amazon Linux 2023**
- Tipo: **t3.large** (8 GB). Con t3.medium no caben los 3 microservicios junto a Kafka.
- Par de claves: **el mismo `Pedidos360`**
- Red: la VPC y la subred **del paso 1**, IP pública habilitada, SG `pedidos360-streaming-sg`
- Almacenamiento: **30 GiB gp3**
- No instales nada a mano: el pipeline corre `preparar-ec2.sh` (Docker, compose, swap, ajustes de kernel).

## Paso 4: IP elástica

EC2 → Direcciones IP elásticas → Asignar → Asociar a `pedidos360-streaming`.
Anota la **IP elástica** y la **IP privada** (detalle de la instancia, ej. `172.31.x.x`).

## Paso 5: secrets nuevos en GitHub

Repositorio → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Valor |
|---|---|
| `KAFKA_EC2_HOST` | IP elástica de la EC2-3 |
| `KAFKA_PRIVATE_IP` | IP privada de la EC2-3 |
| `KAFKA_UI_USER` | usuario para entrar a Kafka-UI (ej. `admin`) |
| `KAFKA_UI_PASSWORD` | una clave larga |

Se reutilizan los que ya existen: `EC2_USERNAME`, `EC2_SSH_KEY`, `DB_USERNAME`, `DB_PASSWORD`.

## Paso 6: desplegar

El push a `BlackDev` solo compila y prueba. Para desplegar: pestaña **Actions** → *Deploy Pedidos360* →
**Run workflow**. Orden: EC2-3 (Kafka) → EC2-2 (RabbitMQ) → EC2-1 (frontend + ms-pedidos).
Las 3 EC2 tienen que estar encendidas.

## Paso 7: API Gateway

Agrega rutas (misma integración HTTP y el mismo JWT Authorizer de siempre) hacia `http://<IP elástica EC2-3>`:

| Ruta | Integración |
|---|---|
| `ANY /api/report/{proxy+}` | `http://<ip-ec2-3>:8084/api/report/{proxy}` |
| `ANY /api/audit` y `ANY /api/audit/{proxy+}` | `http://<ip-ec2-3>:8085/api/audit...` |
| `ANY /api/kafka-admin/{proxy+}` | `http://<ip-ec2-3>:8086/api/kafka-admin/{proxy}` |

## Paso 8: verificar

```bash
ssh -i Pedidos360.pem ec2-user@<IP_ELASTICA_EC2_3> "sudo docker compose -f streaming/docker-compose.yml --profile app ps"
```
- Kafka-UI: `http://<IP_ELASTICA_EC2_3>:8090` → 3 brokers, tópicos con 3 particiones y RF 3, grupos `report-service`
  y `audit-service`.
- Circuito cerrado: ejecuta en la EC2-1 y luego en la EC2-2:
  `timeout 3 bash -c '</dev/tcp/<ip-privada-ec2-3>/9092' && echo ABIERTO || echo BLOQUEADO`
  → EC2-1 dice ABIERTO, EC2-2 dice BLOQUEADO.

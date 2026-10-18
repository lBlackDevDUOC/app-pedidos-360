# Guía AWS: servidor de mensajería (EC2-2) para la EP3

Arquitectura final:

```
                         Internet
                            │
                   AWS API Gateway (JWT Authorizer Entra ID)
                    │                          │
          /api/pedidos, /api/clientes     /api/admin/*
                    │                          │
┌───────────── VPC (default) ──────────────────┼──────────────────────────┐
│  EC2-1  pedidos360 (t3.micro)        EC2-2  pedidos360-mensajeria (t3.micro)
│  ├─ frontend   :80                   ├─ ms-rabbit-admin :8083 ◀──┘
│  ├─ ms-pedidos :8080 ── AMQP 5672 ──▶├─ RabbitMQ :5672 / :15672 (UI)
│  └─ PostgreSQL                        ├─ ms-documentos
│                 (IP privada,          └─ ms-notificaciones
│                  no sale a internet)
└──────────────────────────────────────────────────────────────────────────┘
```

Tiempo estimado: unos 30 minutos.

---

## Paso 1: Anotar los datos de la EC2-1 (la actual)

Consola AWS → **EC2** → **Instancias** → selecciona la instancia actual → pestaña **Redes** (Networking). Anota:

| Dato | Ejemplo |
|---|---|
| ID de VPC | `vpc-0abc...` |
| ID de subred | `subnet-0abc...` |
| Grupo de seguridad (pestaña **Seguridad**) | `sg-0aaa...` ← lo usaremos para autorizar a la EC2-1 |

## Paso 2: Crear el Security Group del servidor de mensajería

EC2 → **Grupos de seguridad** → **Crear grupo de seguridad**

- Nombre: `pedidos360-mensajeria-sg`
- VPC: **la misma del paso 1**
- **Reglas de entrada:**

| Tipo | Puerto | Origen | Por qué |
|---|---|---|---|
| SSH | 22 | `0.0.0.0/0` | GitHub Actions despliega por SSH (sus IPs cambian; se entra solo con tu llave `.pem`) |
| TCP personalizado | 5672 | **el grupo de seguridad de la EC2-1** (`sg-0aaa...`, escríbelo en "Origen") | Solo ms-pedidos puede hablar AMQP con RabbitMQ |
| TCP personalizado | 8083 | `0.0.0.0/0` | API de ms-rabbit-admin, la llama el API Gateway (igual que hoy el 8080 de la EC2-1). Exige JWT |
| TCP personalizado | 15672 | **Mi IP** | Panel web de RabbitMQ, solo para ti (presentación) |

- Reglas de salida: dejar la que viene por defecto (todo permitido).

> Esta es la parte de "seguridad por red" para la defensa: RabbitMQ (5672) **no está expuesto a internet**. Solo lo
> alcanza quien tenga el Security Group de la EC2-1, y por la IP privada dentro de la VPC.

## Paso 3: Lanzar la EC2-2

EC2 → **Lanzar instancias**

- Nombre: `pedidos360-mensajeria`
- AMI: **Amazon Linux 2023** (la misma de la EC2-1)
- Tipo: **t3.micro**
- Par de claves: **el mismo de la EC2-1** (`Pedidos360`). Así GitHub Actions usa la misma llave (`EC2_SSH_KEY`).
- Configuración de red → **Editar**:
  - VPC: la del paso 1
  - Subred: **la misma** del paso 1
  - Asignar IP pública automáticamente: **Habilitar**
  - Firewall: **Seleccionar grupo de seguridad existente** → `pedidos360-mensajeria-sg`
- Almacenamiento: **16 GiB gp3** (imágenes Docker + 2 GB de swap + mensajes persistentes)
- **Lanzar**. No hay que instalar nada a mano: el pipeline instala Docker, docker compose y la swap la primera vez.

## Paso 4: IP elástica (recomendado)

Sin ella, la IP pública cambia cada vez que detienes y enciendes la instancia, y habría que actualizar el API Gateway y los secrets.

EC2 → **Direcciones IP elásticas** → **Asignar** → selecciónala → **Acciones → Asociar** → instancia `pedidos360-mensajeria`.

Anota:
- **IP pública (elástica)** de la EC2-2, ej. `3.92.10.20`
- **IP privada** de la EC2-2 (en el detalle de la instancia), ej. `172.31.40.15`

## Paso 5: Secrets en GitHub

Repositorio → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Valor |
|---|---|
| `MQ_EC2_HOST` | IP **pública** (elástica) de la EC2-2 |
| `MQ_PRIVATE_IP` | IP **privada** de la EC2-2 |
| `RABBITMQ_USER` | un usuario, ej. `pedidos360` |
| `RABBITMQ_PASSWORD` | una contraseña larga (no uses `guest`) |

`EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY`, `DB_USERNAME` y `DB_PASSWORD` ya existen y no cambian.

## Paso 6: Ruta del administrador en el API Gateway

API Gateway → tu HTTP API → **Routes** → **Create**. Crea tres rutas, igual que en la EV2: una por método, sin `ANY`, para no capturar el preflight `OPTIONS`.

| Método | Ruta |
|---|---|
| `GET` | `/api/admin/{proxy+}` |
| `POST` | `/api/admin/{proxy+}` |
| `DELETE` | `/api/admin/{proxy+}` |

En cada una:
- **Attach authorization** → `EntraID-Authorizer`
- **Attach integration** → **Create integration** → tipo **HTTP URI**, método igual al de la ruta, URL:
  `http://<IP-ELASTICA-EC2-2>:8083/api/admin/{proxy}`

En **CORS**, revisa que *Access-Control-Allow-Methods* incluya `DELETE` y *Allow-Headers* incluya `authorization` y `content-type`.
Si el stage no tiene auto-deploy, presiona **Deploy**.

## Paso 7: Desplegar y verificar

1. Merge de la rama `ep3-rabbitmq` a `BlackDev` y push. O desde GitHub → **Actions → Deploy Pedidos360 → Run workflow**.
2. En Actions, el paso "Pruebas y empaquetado" debe mostrar los tests en verde y luego el despliegue en las dos EC2.
3. Verificar:
   - `http://<IP-ELASTICA-EC2-2>:15672` → panel de RabbitMQ (usuario y clave de los secrets). En Queues deben aparecer las colas `documentos.*`, `notificaciones.*` y `pedidos.*`.
   - En el panel, la columna **Consumers** de cada cola de trabajo debe ser 1 o más (los 3 microservicios conectados, y ms-pedidos conectado desde la EC2-1).
   - Crear un pedido en el frontend publicado: a los segundos aparece el N° de comprobante.

## Costos

- Una t3.micro está en la capa gratuita (750 h/mes). **Dos encendidas todo el mes suman 1.500 h**: la segunda cuesta cerca de USD 7–8 al mes, más unos USD 3,6 al mes por cada IP pública.
- Para ahorrar, deténlas (Instancia → Detener) cuando no las uses y enciéndelas antes de la presentación. Los datos se conservan (volúmenes Docker en el disco EBS).

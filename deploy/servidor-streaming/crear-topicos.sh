#!/bin/bash
# Crea (o actualiza) los tópicos de Pedidos360 con su configuración. Es idempotente: se puede correr en cada
# despliegue. Si el tópico ya existe, solo se le vuelven a aplicar las configuraciones de este archivo.
#
# | Tópico                     | Part. | RF | cleanup.policy | retención | Uso                                        |
# |----------------------------|-------|----|----------------|-----------|--------------------------------------------|
# | orders.events              | 3     | 3  | delete         | 7 días    | Hechos de negocio del pedido → reportería  |
# | audit.timeline             | 3     | 3  | compact,delete | 30 días   | Quién/qué/cuándo/desde dónde → auditoría   |
# | orders.events.report.DLT   | 3     | 3  | delete         | 14 días   | Eventos que ms-report no pudo procesar     |
# | orders.events.audit.DLT    | 3     | 3  | delete         | 14 días   | Eventos que ms-audit no pudo procesar      |
# | audit.timeline.audit.DLT   | 3     | 3  | delete         | 14 días   | Registros de auditoría que fallaron        |
set -e

BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
DIA_MS=86400000

# $1 nombre, $2 particiones, $3 configs separadas por coma
crear_topico() {
  local nombre=$1 particiones=$2 configs=$3
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$nombre" --partitions "$particiones" --replication-factor 3
  # Se reaplica siempre: si alguien cambió la config a mano, el despliegue la deja como está versionada
  kafka-configs --bootstrap-server "$BOOTSTRAP" --alter --entity-type topics --entity-name "$nombre" \
    --add-config "$configs"
  echo "OK  $nombre"
}

echo "Creando tópicos en $BOOTSTRAP ..."

# Flujo canónico de eventos de negocio. Key = id del pedido → todos los eventos de un pedido caen en la misma
# partición y se leen en orden (CREADO antes que ACEPTADO). 7 días bastan: report guarda los agregados en su BD.
crear_topico orders.events 3 \
  "cleanup.policy=delete,retention.ms=$((7 * DIA_MS)),min.insync.replicas=2"

# Línea de tiempo de auditoría. Key = id del evento: la compactación solo elimina duplicados del MISMO evento
# (reintentos del productor) y nunca borra historia; "delete" la purga después de 30 días.
# segment.ms de 1 día para que los segmentos se cierren y la compactación/borrado pueda actuar.
crear_topico audit.timeline 3 \
  "cleanup.policy=[compact,delete],retention.ms=$((30 * DIA_MS)),segment.ms=$DIA_MS,min.insync.replicas=2"

# Dead Letter Topics: uno por consumidor, para saber quién falló y reprocesar sin afectar al resto
for dlt in orders.events.report.DLT orders.events.audit.DLT audit.timeline.audit.DLT; do
  crear_topico "$dlt" 3 "cleanup.policy=delete,retention.ms=$((14 * DIA_MS)),min.insync.replicas=2"
done

echo
kafka-topics --bootstrap-server "$BOOTSTRAP" --describe --exclude-internal

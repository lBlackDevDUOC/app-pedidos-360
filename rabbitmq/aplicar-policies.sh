#!/bin/sh
# Policies de retención aplicadas vía API HTTP de RabbitMQ (Management).
# Las colas del sistema ya declaran sus propios límites con x-arguments (TTL y max-length);
# estas policies son la red de seguridad para cualquier otra cola. A cada cola se le aplica
# UNA sola policy (la de mayor prioridad que calce con su nombre). Si una cola tiene
# el límite por argumento y por policy, RabbitMQ aplica el MENOR de los dos.
set -e
# Las policies son globales del clúster: basta con aplicarlas en un nodo
API="${RABBITMQ_API:-http://rabbitmq:15672/api}"
AUTH="$RABBITMQ_USER:$RABBITMQ_PASSWORD"

# 1) Ninguna cola puede crecer sin control: máximo 100.000 mensajes (descarta los más antiguos).
curl -sf -u "$AUTH" -X PUT "$API/policies/%2F/retencion-global" \
  -H "content-type: application/json" \
  -d '{"pattern":".*","apply-to":"queues","priority":0,
       "definition":{"max-length":100000,"overflow":"drop-head"}}'
echo "policy retencion-global aplicada"

# 2) DLQs: cuarentena acotada a 7 días y 10.000 mensajes (limpieza automática).
curl -sf -u "$AUTH" -X PUT "$API/policies/%2F/retencion-dlq" \
  -H "content-type: application/json" \
  -d '{"pattern":"[.]dlq$","apply-to":"queues","priority":1,
       "definition":{"max-length":10000,"message-ttl":604800000}}'
echo "policy retencion-dlq aplicada"

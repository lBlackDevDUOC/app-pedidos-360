#!/bin/sh
# Despliegue del servidor de aplicaciones (EC2-1). Lo ejecuta GitHub Actions dentro de /home/ec2-user/apps
# con las variables de entorno que vienen de los secrets (nada de IPs ni claves escritas en el repositorio).
#
#   Internet ─▶ API Gateway ─▶ :8080 ms-bff ─┬─▶ ms-pedidos  (red Docker, sin puerto publicado)
#                                            ├─▶ ms-catalog  (red Docker, sin puerto publicado)
#                                            ├─▶ EC2-3 :8084-8086 report / audit / kafka-admin (IP privada)
#                                            └─▶ EC2-2 :8083 rabbit-admin (IP privada)
#   Internet ─▶ :80 frontend (Nginx)
set -e
: "${DB_USERNAME:?falta DB_USERNAME}" "${DB_PASSWORD:?falta DB_PASSWORD}"
: "${MQ_PRIVATE_IP:?falta MQ_PRIVATE_IP}" "${KAFKA_PRIVATE_IP:?falta KAFKA_PRIVATE_IP}"
RED=pedidos360-net
JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Xms64m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

# Swap de 2 GB: 3 JVM + PostgreSQL + Nginx en una instancia chica
if [ ! -f /swapfile ]; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

docker network create $RED 2>/dev/null || true

# ---------------------------------------------------------------- PostgreSQL (pedidos360_db + catalog_db)
if [ -z "$(docker ps -q -f name=^pedidos360-db$)" ]; then
  docker rm -f pedidos360-db 2>/dev/null || true
  docker run -d --name pedidos360-db --network $RED \
    -e POSTGRES_DB=pedidos360_db -e POSTGRES_USER="$DB_USERNAME" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -v postgres-data:/var/lib/postgresql/data --restart unless-stopped postgres:16-alpine
fi
docker network connect $RED pedidos360-db 2>/dev/null || true
# El puerto 5432 ya no se publica: solo lo alcanzan los contenedores de la red
until docker exec pedidos360-db pg_isready -U "$DB_USERNAME" >/dev/null 2>&1; do sleep 2; done
# Cada microservicio con su propia base: se crea catalog_db si no existe
docker exec pedidos360-db psql -U "$DB_USERNAME" -d pedidos360_db -tc \
  "SELECT 1 FROM pg_database WHERE datname='catalog_db'" | grep -q 1 \
  || docker exec pedidos360-db createdb -U "$DB_USERNAME" catalog_db

# ---------------------------------------------------------------- Imágenes desde los .jar ya probados en CI
docker build -q -f Dockerfile.jar --build-arg JAR=pedidos/app.jar -t pedidos360-backend .
docker build -q -f Dockerfile.jar --build-arg JAR=catalog/app.jar -t pedidos360-catalog .
docker build -q -f Dockerfile.jar --build-arg JAR=bff/app.jar     -t pedidos360-bff .
docker build -q -t pedidos360-frontend frontend

reiniciar() { docker rm -f "$1" 2>/dev/null || true; }

# ms-catalog
reiniciar pedidos360-catalog
docker run -d --name pedidos360-catalog --network $RED --restart unless-stopped --memory 384m \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pedidos360-db:5432/catalog_db \
  -e DB_USERNAME="$DB_USERNAME" -e DB_PASSWORD="$DB_PASSWORD" -e JAVA_TOOL_OPTIONS="$JAVA_OPTS" \
  pedidos360-catalog

# ms-pedidos (productor RabbitMQ + Kafka)
reiniciar pedidos360-backend
docker run -d --name pedidos360-backend --network $RED --restart unless-stopped --memory 448m \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pedidos360-db:5432/pedidos360_db \
  -e DB_USERNAME="$DB_USERNAME" -e DB_PASSWORD="$DB_PASSWORD" \
  -e SPRING_RABBITMQ_ADDRESSES="$MQ_PRIVATE_IP:5672,$MQ_PRIVATE_IP:5673" \
  -e SPRING_RABBITMQ_ADDRESSSHUFFLEMODE=random \
  -e SPRING_RABBITMQ_USERNAME="$RABBITMQ_USER" -e SPRING_RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
  -e KAFKA_BOOTSTRAP_SERVERS="$KAFKA_PRIVATE_IP:9092,$KAFKA_PRIVATE_IP:9093,$KAFKA_PRIVATE_IP:9094" \
  -e CATALOG_URL=http://pedidos360-catalog:8087 \
  -e JAVA_TOOL_OPTIONS="$JAVA_OPTS" \
  pedidos360-backend

# ms-bff: el único puerto de API publicado (8080, el mismo al que ya apuntaba el API Gateway)
reiniciar pedidos360-bff
docker run -d --name pedidos360-bff --network $RED --restart unless-stopped --memory 384m -p 8080:8088 \
  -e PEDIDOS_URL=http://pedidos360-backend:8080 \
  -e CATALOG_URL=http://pedidos360-catalog:8087 \
  -e REPORT_URL="http://$KAFKA_PRIVATE_IP:8084" \
  -e AUDIT_URL="http://$KAFKA_PRIVATE_IP:8085" \
  -e KAFKA_ADMIN_URL="http://$KAFKA_PRIVATE_IP:8086" \
  -e RABBIT_ADMIN_URL="http://$MQ_PRIVATE_IP:8083" \
  ${FRONTEND_ORIGIN:+-e BFF_CORS_ORIGINS="$FRONTEND_ORIGIN"} \
  -e JAVA_TOOL_OPTIONS="$JAVA_OPTS" \
  pedidos360-bff

# Frontend (Angular compilado + Nginx)
reiniciar pedidos360-frontend
docker run -d --name pedidos360-frontend --network $RED --restart unless-stopped -p 80:80 pedidos360-frontend

docker image prune -f >/dev/null
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

#!/bin/sh
# Prepara la EC2 del servidor de streaming (Amazon Linux 2023). Es idempotente: si algo ya está, lo omite.
set -e

# 1) Docker
if ! command -v docker >/dev/null 2>&1; then
  sudo dnf install -y docker
  sudo systemctl enable --now docker
  sudo usermod -aG docker ec2-user
fi

# 2) Plugin docker compose
if ! sudo docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

# 3) Swap de 2 GB: colchón para el arranque simultáneo de 6 JVM (3 Zookeeper + 3 brokers) y los microservicios
if [ ! -f /swapfile ]; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# 4) Kafka mapea a memoria (mmap) los índices de cada segmento de log. Con el límite por defecto (65530)
#    un broker con muchas particiones se cae con "Map failed" aunque le sobre RAM.
if [ "$(sysctl -n vm.max_map_count)" -lt 262144 ]; then
  echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-kafka.conf >/dev/null
  sudo sysctl --system >/dev/null
fi

# 5) Usar la swap solo en emergencia: si Kafka/Zookeeper se pausan leyendo swap, el ensemble cree que el nodo murió
if [ "$(sysctl -n vm.swappiness)" -gt 10 ]; then
  echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf >/dev/null
  sudo sysctl --system >/dev/null
fi

echo "EC2 lista: $(sudo docker --version), $(sudo docker compose version)"
echo "RAM $(free -m | awk '/Mem/ {print $2}') MB, swap $(free -m | awk '/Swap/ {print $2}') MB," \
     "vm.max_map_count=$(sysctl -n vm.max_map_count), vm.swappiness=$(sysctl -n vm.swappiness)"

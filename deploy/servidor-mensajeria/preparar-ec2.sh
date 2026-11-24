#!/bin/sh
# Prepara la EC2 del servidor de mensajería (Amazon Linux 2023). Es idempotente: si algo ya está, lo omite.
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

# 3) Swap de 2 GB: RabbitMQ + 3 JVM superan por poco 1 GB en los picos de arranque
if [ ! -f /swapfile ]; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "EC2 lista: $(sudo docker --version), $(sudo docker compose version), swap $(free -m | awk '/Swap/ {print $2}') MB"

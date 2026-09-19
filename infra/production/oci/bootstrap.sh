#!/bin/bash
set -euo pipefail
. /etc/os-release
test "$ID" = ubuntu && test "$VERSION_ID" = 24.04 || { echo 'Requires official Ubuntu 24.04 ARM64' >&2; exit 1; }
test "$(dpkg --print-architecture)" = arm64 || { echo 'Requires ARM64' >&2; exit 1; }
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl xz-utils
install -m 0755 -d /etc/apt/keyrings
curl -fsS https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
printf '%s\n' 'deb [arch=arm64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable' > /etc/apt/sources.list.d/docker.list
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
curl -fsS https://nodejs.org/dist/v24.19.0/node-v24.19.0-linux-arm64.tar.xz -o /var/tmp/chanter-node.tar.xz
printf '%s\n' '01443c1e1a29e531ccad5a46fefa6df490d2189c49f7955904aecdbb0fe86fdc  /var/tmp/chanter-node.tar.xz' | sha256sum --check -
install -d /opt/chanter-node
tar -xJf /var/tmp/chanter-node.tar.xz --strip-components=1 -C /opt/chanter-node
ln -sf /opt/chanter-node/bin/node /usr/local/bin/node
rm /var/tmp/chanter-node.tar.xz
usermod -aG docker ubuntu
install -d -o ubuntu -g ubuntu -m 0700 /srv/chanter /srv/chanter/releases
systemctl enable --now docker
docker compose version
# No runtime secrets, seed users, paid services or deployments are created here.

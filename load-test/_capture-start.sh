#!/usr/bin/env bash
# Start a full-mesh packet capture for the SwiftPay load test.
#
# The SwiftPay services talk to each other (Kafka, PostgreSQL, Redis) *inside*
# the Docker network, which lives in the colima VM -- that traffic never reaches
# the macOS host. So the capture runs on the Docker bridge interface inside the
# VM, where every packet is either SwiftPay inter-service traffic or an inbound
# API call after DNAT.
#
# Usage:  load-test/_capture-start.sh
# Stop :  load-test/_capture-stop.sh   (or it auto-stops after CAP_SECONDS)
set -euo pipefail

export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"
COMPOSE_NET="${COMPOSE_NET:-swiftpay-pyrogroup-suhasallampalli_default}"
PCAP_VM="${PCAP_VM:-/mnt/lima-colima/swiftpay-loadtest.pcap}"   # VM-local ext4, not virtiofs
SNAPLEN="${SNAPLEN:-160}"          # full L2/L3/L4 headers + protocol-identifying payload
CAP_SECONDS="${CAP_SECONDS:-4600}" # 4000 s test + setup/drain margin

NETID="$(docker network inspect "$COMPOSE_NET" -f '{{.Id}}' | cut -c1-12)"
BR="br-$NETID"
echo "docker network $COMPOSE_NET -> bridge $BR"

# tcpdump is not in the stock colima image; install it once.
colima ssh -- bash -c 'command -v tcpdump >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq tcpdump; }'

colima ssh -- sudo sh -c "rm -f '$PCAP_VM'; \
  nohup tcpdump -i '$BR' -n -s '$SNAPLEN' -B 65536 -w '$PCAP_VM' >/tmp/loadcap.log 2>&1 & \
  echo \$! > /tmp/loadcap.pid; \
  nohup sh -c 'sleep $CAP_SECONDS; kill -INT \$(cat /tmp/loadcap.pid)' >/dev/null 2>&1 &"
sleep 4
colima ssh -- sudo bash -c 'cat /tmp/loadcap.log; echo "pid=$(cat /tmp/loadcap.pid)  file='"$PCAP_VM"'"'

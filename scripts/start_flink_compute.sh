#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

JOBMANAGER_CONTAINER="${JOBMANAGER_CONTAINER:-aitradex-flink-jobmanager}"
JAR_PATH="${ROOT_DIR}/flink-compute/target/flink-compute-1.0.0.jar"

echo "[INFO] Building flink-compute jar..."
(
  cd "${ROOT_DIR}/flink-compute"
  mvn -q -DskipTests package
)

if [ ! -f "${JAR_PATH}" ]; then
  echo "[ERROR] jar not found: ${JAR_PATH}"
  exit 1
fi

echo "[INFO] Copying jar to ${JOBMANAGER_CONTAINER}..."
docker cp "${JAR_PATH}" "${JOBMANAGER_CONTAINER}:/opt/flink/usrlib/flink-compute.jar"

KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-redpanda:9092}"
STREAM_TOPIC_MARKET_TICK="${STREAM_TOPIC_MARKET_TICK:-aitradex.market.tick.norm}"
STREAM_TOPIC_ORDER_EVENT="${STREAM_TOPIC_ORDER_EVENT:-aitradex.trade.order.event}"
STREAM_TOPIC_RISK_EVENT="${STREAM_TOPIC_RISK_EVENT:-aitradex.trade.risk.event}"
STREAM_TOPIC_WORKFLOW_EVENT="${STREAM_TOPIC_WORKFLOW_EVENT:-aitradex.workflow.run.event}"
JDBC_DATABASE_URL="${JDBC_DATABASE_URL:-jdbc:postgresql://postgres:5432/aitradex}"
POSTGRES_USER="${POSTGRES_USER:-aitradex}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-aitradex}"
FLINK_SNAPSHOT_INTERVAL_MS="${FLINK_SNAPSHOT_INTERVAL_MS:-5000}"

echo "[INFO] Submitting flink-compute job..."
docker exec \
  -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
  -e STREAM_TOPIC_MARKET_TICK="${STREAM_TOPIC_MARKET_TICK}" \
  -e STREAM_TOPIC_ORDER_EVENT="${STREAM_TOPIC_ORDER_EVENT}" \
  -e STREAM_TOPIC_RISK_EVENT="${STREAM_TOPIC_RISK_EVENT}" \
  -e STREAM_TOPIC_WORKFLOW_EVENT="${STREAM_TOPIC_WORKFLOW_EVENT}" \
  -e JDBC_DATABASE_URL="${JDBC_DATABASE_URL}" \
  -e POSTGRES_USER="${POSTGRES_USER}" \
  -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
  -e FLINK_SNAPSHOT_INTERVAL_MS="${FLINK_SNAPSHOT_INTERVAL_MS}" \
  "${JOBMANAGER_CONTAINER}" \
  /opt/flink/bin/flink run \
  -d \
  -m flink-jobmanager:8081 \
  -c com.aitradex.flink.FlinkComputeJob \
  /opt/flink/usrlib/flink-compute.jar

echo "[OK] flink-compute submitted. Check http://localhost:8081"

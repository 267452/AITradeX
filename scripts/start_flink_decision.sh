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
STREAM_TOPIC_RISK_EVENT="${STREAM_TOPIC_RISK_EVENT:-aitradex.trade.risk.event}"
STREAM_TOPIC_DECISION_SIGNAL="${STREAM_TOPIC_DECISION_SIGNAL:-aitradex.trade.decision.signal}"
JDBC_DATABASE_URL="${JDBC_DATABASE_URL:-jdbc:postgresql://postgres:5432/aitradex}"
POSTGRES_USER="${POSTGRES_USER:-aitradex}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-aitradex}"
FLINK_DECISION_WINDOW_SEC="${FLINK_DECISION_WINDOW_SEC:-30}"
FLINK_DECISION_UP_BPS="${FLINK_DECISION_UP_BPS:-18}"
FLINK_DECISION_DOWN_BPS="${FLINK_DECISION_DOWN_BPS:-18}"
FLINK_DECISION_COOLDOWN_SEC="${FLINK_DECISION_COOLDOWN_SEC:-20}"
FLINK_DECISION_MAX_RISK_REJECT_RATE_5M="${FLINK_DECISION_MAX_RISK_REJECT_RATE_5M:-35}"

echo "[INFO] Submitting flink-decision job..."
docker exec \
  -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
  -e STREAM_TOPIC_MARKET_TICK="${STREAM_TOPIC_MARKET_TICK}" \
  -e STREAM_TOPIC_RISK_EVENT="${STREAM_TOPIC_RISK_EVENT}" \
  -e STREAM_TOPIC_DECISION_SIGNAL="${STREAM_TOPIC_DECISION_SIGNAL}" \
  -e JDBC_DATABASE_URL="${JDBC_DATABASE_URL}" \
  -e POSTGRES_USER="${POSTGRES_USER}" \
  -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
  -e FLINK_DECISION_WINDOW_SEC="${FLINK_DECISION_WINDOW_SEC}" \
  -e FLINK_DECISION_UP_BPS="${FLINK_DECISION_UP_BPS}" \
  -e FLINK_DECISION_DOWN_BPS="${FLINK_DECISION_DOWN_BPS}" \
  -e FLINK_DECISION_COOLDOWN_SEC="${FLINK_DECISION_COOLDOWN_SEC}" \
  -e FLINK_DECISION_MAX_RISK_REJECT_RATE_5M="${FLINK_DECISION_MAX_RISK_REJECT_RATE_5M}" \
  "${JOBMANAGER_CONTAINER}" \
  /opt/flink/bin/flink run \
  -d \
  -m flink-jobmanager:8081 \
  -c com.aitradex.flink.FlinkDecisionJob \
  /opt/flink/usrlib/flink-compute.jar

echo "[OK] flink-decision submitted. Check http://localhost:8081"

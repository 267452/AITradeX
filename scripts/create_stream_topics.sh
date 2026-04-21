#!/usr/bin/env bash
set -euo pipefail

CONTAINER="${REDPANDA_CONTAINER:-aitradex-redpanda}"

TOPICS=(
  "${STREAM_TOPIC_MARKET_TICK:-aitradex.market.tick.norm}"
  "${STREAM_TOPIC_ORDER_EVENT:-aitradex.trade.order.event}"
  "${STREAM_TOPIC_RISK_EVENT:-aitradex.trade.risk.event}"
  "${STREAM_TOPIC_WORKFLOW_EVENT:-aitradex.workflow.run.event}"
  "${STREAM_TOPIC_DECISION_SIGNAL:-aitradex.trade.decision.signal}"
)

for topic in "${TOPICS[@]}"; do
  echo "[INFO] creating topic ${topic}"
  docker exec "${CONTAINER}" rpk topic create "${topic}" --partitions 3 --replicas 1 || true
done

echo "[OK] topic initialization complete"

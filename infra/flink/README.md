# Flink Realtime Pipeline (Scheme A + Scheme B)

This directory contains local runbooks for two Flink modes:

- Scheme A: realtime metrics snapshot (`flink_compute_snapshot`)
- Scheme B: low-latency decision signal (`flink_decision_signal`)

## Components

- `redpanda` as Kafka-compatible event bus
- `flink-jobmanager` + `flink-taskmanager` as compute runtime
- `flink-compute` module as stream jobs:
  - `com.aitradex.flink.FlinkComputeJob` (Scheme A)
  - `com.aitradex.flink.FlinkDecisionJob` (Scheme B)
- PostgreSQL sink tables:
  - `flink_compute_snapshot`
  - `flink_hot_symbol_snapshot`
  - `flink_decision_signal`

## 1) Start infra

```bash
docker compose up -d postgres redis api
docker compose -f docker-compose.flink.yml up -d
```

## 2) Create stream topics

```bash
scripts/create_stream_topics.sh
```

Topics include:

- `STREAM_TOPIC_MARKET_TICK`
- `STREAM_TOPIC_ORDER_EVENT`
- `STREAM_TOPIC_RISK_EVENT`
- `STREAM_TOPIC_WORKFLOW_EVENT`
- `STREAM_TOPIC_DECISION_SIGNAL`

## 3) Enable stream publish in API

Set environment variables in `.env`:

```bash
STREAM_ENABLED=true
STREAM_BOOTSTRAP_SERVERS=redpanda:9092
STREAM_TOPIC_MARKET_TICK=aitradex.market.tick.norm
STREAM_TOPIC_ORDER_EVENT=aitradex.trade.order.event
STREAM_TOPIC_RISK_EVENT=aitradex.trade.risk.event
STREAM_TOPIC_WORKFLOW_EVENT=aitradex.workflow.run.event
STREAM_TOPIC_DECISION_SIGNAL=aitradex.trade.decision.signal
STREAM_INGEST_TOKEN=your-ingest-token
```

Restart API after updating `.env`:

```bash
docker compose up -d --build api
```

## 4) Submit Flink jobs

Scheme A:

```bash
scripts/start_flink_compute.sh
```

Scheme B:

```bash
scripts/start_flink_decision.sh
```

Flink UI: `http://localhost:8081`

## 5) Ingest external ticks

Use:

- `POST /api/market/ticks/ingest`
- Header: `X-Stream-Token: your-ingest-token`

Example:

```bash
curl -X POST "http://localhost:8000/api/market/ticks/ingest" \
  -H "Authorization: Bearer <token>" \
  -H "X-Stream-Token: your-ingest-token" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "source": "vendor-a",
        "exchange": "okx",
        "symbol": "BTC-USDT",
        "event_time": "2026-01-01T12:00:00Z",
        "last_price": 66234.12,
        "bid1": 66234.01,
        "ask1": 66234.22,
        "volume": 0.52,
        "turnover": 34429.74,
        "source_event_id": "tick-1"
      }
    ]
  }'
```

## 6) Verify Scheme A snapshot

```sql
SELECT computed_at, source_events_1m, processed_events_1m, order_fill_rate_5m, risk_reject_rate_5m
FROM flink_compute_snapshot
ORDER BY computed_at DESC
LIMIT 10;
```

## 7) Verify Scheme B decision signals

```sql
SELECT symbol, side, confidence, price_change_bps, risk_reject_rate_5m, computed_at
FROM flink_decision_signal
ORDER BY computed_at DESC
LIMIT 20;
```

API query:

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8000/api/monitor/flink/decision-signals?limit=20"
```

## 8) Scheme B runtime knobs

```bash
FLINK_DECISION_WINDOW_SEC=30
FLINK_DECISION_UP_BPS=18
FLINK_DECISION_DOWN_BPS=18
FLINK_DECISION_COOLDOWN_SEC=20
FLINK_DECISION_MAX_RISK_REJECT_RATE_5M=35
```

Set `FLINK_COMPUTE_ENGINE=decision` in API env to let `/api/monitor/flink/metrics` return decision-mode metrics.

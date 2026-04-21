package com.aitradex.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlinkComputeJob {
    private static final Logger logger = LoggerFactory.getLogger(FlinkComputeJob.class);

    public static void main(String[] args) throws Exception {
        JobConfig config = JobConfig.fromEnv();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStream<MetricEvent> marketEvents = env
                .fromSource(kafkaSource(config.bootstrapServers, config.marketTickTopic, config.groupPrefix + "-market"),
                        WatermarkStrategy.noWatermarks(), "market-tick-source")
                .flatMap(new MetricEventParser(EventType.MARKET))
                .name("market-event-parser");

        DataStream<MetricEvent> orderEvents = env
                .fromSource(kafkaSource(config.bootstrapServers, config.orderEventTopic, config.groupPrefix + "-order"),
                        WatermarkStrategy.noWatermarks(), "order-event-source")
                .flatMap(new MetricEventParser(EventType.ORDER))
                .name("order-event-parser");

        DataStream<MetricEvent> riskEvents = env
                .fromSource(kafkaSource(config.bootstrapServers, config.riskEventTopic, config.groupPrefix + "-risk"),
                        WatermarkStrategy.noWatermarks(), "risk-event-source")
                .flatMap(new MetricEventParser(EventType.RISK))
                .name("risk-event-parser");

        DataStream<MetricEvent> workflowEvents = env
                .fromSource(kafkaSource(config.bootstrapServers, config.workflowEventTopic, config.groupPrefix + "-workflow"),
                        WatermarkStrategy.noWatermarks(), "workflow-event-source")
                .flatMap(new MetricEventParser(EventType.WORKFLOW))
                .name("workflow-event-parser");

        DataStream<SnapshotRecord> snapshots = marketEvents
                .union(orderEvents, riskEvents, workflowEvents)
                .keyBy(item -> 0)
                .process(new RealtimeSnapshotFunction(config.snapshotIntervalMs))
                .name("realtime-snapshot-compute");

        snapshots.addSink(new PostgresSnapshotSink(config)).name("postgres-snapshot-sink");

        logger.info("Starting flink compute job, kafka={}, topics=[{},{},{},{}], postgres={}",
                config.bootstrapServers,
                config.marketTickTopic,
                config.orderEventTopic,
                config.riskEventTopic,
                config.workflowEventTopic,
                config.jdbcUrl);

        env.execute("aitradex-flink-compute");
    }

    private static KafkaSource<String> kafkaSource(String bootstrapServers, String topic, String groupId) {
        return KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }

    enum EventType {
        MARKET,
        ORDER,
        RISK,
        WORKFLOW
    }

    static final class MetricEvent implements Serializable {
        EventType type;
        long eventTimeMs;
        String entityId;
        String symbol;
        String status;
        Boolean passed;
        Long latencyMs;

        static MetricEvent market(long eventTimeMs, String symbol) {
            MetricEvent item = new MetricEvent();
            item.type = EventType.MARKET;
            item.eventTimeMs = eventTimeMs;
            item.symbol = symbol;
            return item;
        }

        static MetricEvent order(long eventTimeMs, String orderId, String status, String symbol) {
            MetricEvent item = new MetricEvent();
            item.type = EventType.ORDER;
            item.eventTimeMs = eventTimeMs;
            item.entityId = orderId;
            item.status = status;
            item.symbol = symbol;
            return item;
        }

        static MetricEvent risk(long eventTimeMs, Boolean passed) {
            MetricEvent item = new MetricEvent();
            item.type = EventType.RISK;
            item.eventTimeMs = eventTimeMs;
            item.passed = passed;
            return item;
        }

        static MetricEvent workflow(long eventTimeMs, String runId, String status, Long latencyMs) {
            MetricEvent item = new MetricEvent();
            item.type = EventType.WORKFLOW;
            item.eventTimeMs = eventTimeMs;
            item.entityId = runId;
            item.status = status;
            item.latencyMs = latencyMs;
            return item;
        }
    }

    static final class MetricEventParser implements org.apache.flink.api.common.functions.FlatMapFunction<String, MetricEvent> {
        private final EventType type;
        private final ObjectMapper objectMapper;

        MetricEventParser(EventType type) {
            this.type = type;
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void flatMap(String value, Collector<MetricEvent> out) {
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                JsonNode node = objectMapper.readTree(value);
                long eventTime = parseTime(node.get("event_time"));
                if (type == EventType.MARKET) {
                    String symbol = text(node.get("symbol"));
                    if (symbol != null) {
                        out.collect(MetricEvent.market(eventTime, symbol));
                    }
                    return;
                }
                if (type == EventType.ORDER) {
                    String status = text(node.get("status"));
                    if (status == null) {
                        return;
                    }
                    out.collect(MetricEvent.order(
                            eventTime,
                            text(node.get("order_id")),
                            status,
                            text(node.get("symbol"))));
                    return;
                }
                if (type == EventType.RISK) {
                    JsonNode passedNode = node.get("passed");
                    Boolean passed = passedNode == null || passedNode.isNull() ? null : passedNode.asBoolean();
                    if (passed != null) {
                        out.collect(MetricEvent.risk(eventTime, passed));
                    }
                    return;
                }
                if (type == EventType.WORKFLOW) {
                    String status = text(node.get("status"));
                    if (status == null) {
                        return;
                    }
                    Long latencyMs = asLong(node.get("latency_ms"));
                    out.collect(MetricEvent.workflow(eventTime, text(node.get("run_id")), status, latencyMs));
                }
            } catch (Exception ignored) {
                // keep stream alive on malformed payload
            }
        }

        private long parseTime(JsonNode node) {
            if (node == null || node.isNull()) {
                return System.currentTimeMillis();
            }
            if (node.isNumber()) {
                return node.asLong();
            }
            try {
                return OffsetDateTime.parse(node.asText()).toInstant().toEpochMilli();
            } catch (Exception e) {
                return System.currentTimeMillis();
            }
        }

        private static String text(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            String value = node.asText();
            return value == null || value.isBlank() ? null : value;
        }

        private static Long asLong(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isNumber()) {
                return node.asLong();
            }
            try {
                return Long.parseLong(node.asText());
            } catch (Exception e) {
                return null;
            }
        }
    }

    static final class RealtimeSnapshotFunction extends KeyedProcessFunction<Integer, MetricEvent, SnapshotRecord> {
        private final long intervalMs;
        private transient ValueState<AccumulatorState> state;

        RealtimeSnapshotFunction(long intervalMs) {
            this.intervalMs = Math.max(1000L, intervalMs);
        }

        @Override
        public void open(Configuration parameters) {
            state = getRuntimeContext().getState(new ValueStateDescriptor<>("snapshot-accumulator", AccumulatorState.class));
        }

        @Override
        public void processElement(MetricEvent event, Context ctx, Collector<SnapshotRecord> out) throws Exception {
            AccumulatorState acc = state.value();
            if (acc == null) {
                acc = new AccumulatorState();
            }
            acc.lastEventTimeMs = Math.max(acc.lastEventTimeMs, event.eventTimeMs);
            if (event.type == EventType.MARKET) {
                acc.marketTimes.add(event.eventTimeMs);
                if (event.symbol != null) {
                    acc.marketSymbols.add(new SymbolSample(event.eventTimeMs, event.symbol));
                }
            } else if (event.type == EventType.ORDER) {
                if (event.status != null) {
                    String normalized = event.status.trim().toLowerCase(Locale.ROOT);
                    acc.orderEvents.add(new StatusSample(event.eventTimeMs, normalized));
                    if (event.entityId != null) {
                        if (isOrderTerminal(normalized)) {
                            acc.latestOrderStatus.remove(event.entityId);
                        } else {
                            acc.latestOrderStatus.put(event.entityId, normalized);
                        }
                    }
                }
            } else if (event.type == EventType.RISK) {
                if (event.passed != null) {
                    acc.riskEvents.add(new RiskSample(event.eventTimeMs, event.passed));
                }
            } else if (event.type == EventType.WORKFLOW) {
                if (event.status != null) {
                    String normalized = event.status.trim().toLowerCase(Locale.ROOT);
                    acc.workflowStatusEvents.add(new WorkflowStatusSample(event.eventTimeMs, normalized, event.latencyMs));
                    if (event.entityId != null) {
                        if (isWorkflowTerminal(normalized)) {
                            acc.latestWorkflowStatus.remove(event.entityId);
                        } else {
                            acc.latestWorkflowStatus.put(event.entityId, normalized);
                        }
                    }
                }
            }

            long now = ctx.timerService().currentProcessingTime();
            long nextTimer = alignNext(now, intervalMs);
            if (acc.nextTimerMs <= now || acc.nextTimerMs != nextTimer) {
                ctx.timerService().registerProcessingTimeTimer(nextTimer);
                acc.nextTimerMs = nextTimer;
            }
            state.update(acc);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<SnapshotRecord> out) throws Exception {
            AccumulatorState acc = state.value();
            if (acc == null) {
                acc = new AccumulatorState();
            }
            long now = timestamp;
            prune(acc, now);

            int sourceEvents1m = countAfter(acc.marketTimes, now - 60_000L);
            int sourceEvents5m = countAfter(acc.marketTimes, now - 300_000L);
            int processedEvents1m = sourceEvents1m;
            int processedEvents5m = sourceEvents5m;

            int orderTotal5m = countOrderEvents(acc.orderEvents, now - 300_000L);
            int orderFilled5m = countOrderStatus(acc.orderEvents, now - 300_000L, "filled");
            BigDecimal fillRate5m = percent(orderFilled5m, orderTotal5m);

            int riskTotal5m = countRiskEvents(acc.riskEvents, now - 300_000L);
            int riskRejected5m = countRiskRejected(acc.riskEvents, now - 300_000L);
            BigDecimal riskRejectRate5m = percent(riskRejected5m, riskTotal5m);

            long avgLatency5m = avgWorkflowLatency(acc.workflowStatusEvents, now - 300_000L);
            long p95Latency5m = p95WorkflowLatency(acc.workflowStatusEvents, now - 300_000L);

            long watermarkDelayMs = acc.lastEventTimeMs <= 0 ? 0L : Math.max(0L, now - acc.lastEventTimeMs);
            int queuedOrdersNow = countLatestStatus(acc.latestOrderStatus, "queued");
            int activeRunsNow = countLatestStatus(acc.latestWorkflowStatus, "running");
            int completedRuns5m = countWorkflowStatus(acc.workflowStatusEvents, now - 300_000L, "completed");
            int failedRuns5m = countWorkflowStatus(acc.workflowStatusEvents, now - 300_000L, "failed");
            List<HotSymbolRecord> hotSymbols = topSymbols(acc.marketSymbols, now - 900_000L, 6);

            SnapshotRecord snapshot = new SnapshotRecord();
            snapshot.sourceEvents1m = sourceEvents1m;
            snapshot.sourceEvents5m = sourceEvents5m;
            snapshot.processedEvents1m = processedEvents1m;
            snapshot.processedEvents5m = processedEvents5m;
            snapshot.orderFillRate5m = fillRate5m;
            snapshot.riskRejectRate5m = riskRejectRate5m;
            snapshot.avgWorkflowLatencyMs5m = avgLatency5m;
            snapshot.p95WorkflowLatencyMs5m = p95Latency5m;
            snapshot.watermarkDelayMs = watermarkDelayMs;
            snapshot.queuedOrdersNow = queuedOrdersNow;
            snapshot.activeRunsNow = activeRunsNow;
            snapshot.completedRuns5m = completedRuns5m;
            snapshot.failedRuns5m = failedRuns5m;
            snapshot.computedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC);
            snapshot.hotSymbols = hotSymbols;

            out.collect(snapshot);

            long nextTimer = alignNext(now, intervalMs);
            ctx.timerService().registerProcessingTimeTimer(nextTimer);
            acc.nextTimerMs = nextTimer;
            state.update(acc);
        }

        private void prune(AccumulatorState acc, long nowMs) {
            pruneTimes(acc.marketTimes, nowMs - 300_000L);
            pruneSymbols(acc.marketSymbols, nowMs - 900_000L);
            pruneStatusSamples(acc.orderEvents, nowMs - 300_000L);
            pruneRiskSamples(acc.riskEvents, nowMs - 300_000L);
            pruneWorkflowSamples(acc.workflowStatusEvents, nowMs - 300_000L);
        }

        private int countAfter(List<Long> list, long lowerBoundExclusive) {
            int total = 0;
            for (Long item : list) {
                if (item != null && item >= lowerBoundExclusive) {
                    total += 1;
                }
            }
            return total;
        }

        private int countOrderEvents(List<StatusSample> list, long lowerBoundExclusive) {
            int total = 0;
            for (StatusSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive) {
                    total += 1;
                }
            }
            return total;
        }

        private int countOrderStatus(List<StatusSample> list, long lowerBoundExclusive, String status) {
            int total = 0;
            for (StatusSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && Objects.equals(sample.status, status)) {
                    total += 1;
                }
            }
            return total;
        }

        private int countRiskEvents(List<RiskSample> list, long lowerBoundExclusive) {
            int total = 0;
            for (RiskSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive) {
                    total += 1;
                }
            }
            return total;
        }

        private int countRiskRejected(List<RiskSample> list, long lowerBoundExclusive) {
            int total = 0;
            for (RiskSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && !sample.passed) {
                    total += 1;
                }
            }
            return total;
        }

        private long avgWorkflowLatency(List<WorkflowStatusSample> list, long lowerBoundExclusive) {
            long sum = 0L;
            int count = 0;
            for (WorkflowStatusSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && isWorkflowTerminal(sample.status) && sample.latencyMs != null) {
                    sum += Math.max(0L, sample.latencyMs);
                    count += 1;
                }
            }
            return count == 0 ? 0L : sum / count;
        }

        private long p95WorkflowLatency(List<WorkflowStatusSample> list, long lowerBoundExclusive) {
            List<Long> values = new ArrayList<>();
            for (WorkflowStatusSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && isWorkflowTerminal(sample.status) && sample.latencyMs != null) {
                    values.add(Math.max(0L, sample.latencyMs));
                }
            }
            if (values.isEmpty()) {
                return 0L;
            }
            values.sort(Comparator.naturalOrder());
            int index = (int) Math.ceil(values.size() * 0.95D) - 1;
            index = Math.max(0, Math.min(values.size() - 1, index));
            return values.get(index);
        }

        private int countWorkflowStatus(List<WorkflowStatusSample> list, long lowerBoundExclusive, String targetStatus) {
            int total = 0;
            for (WorkflowStatusSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && Objects.equals(sample.status, targetStatus)) {
                    total += 1;
                }
            }
            return total;
        }

        private int countLatestStatus(Map<String, String> statusMap, String targetStatus) {
            int total = 0;
            for (String value : statusMap.values()) {
                if (targetStatus.equals(value)) {
                    total += 1;
                }
            }
            return total;
        }

        private List<HotSymbolRecord> topSymbols(List<SymbolSample> list, long lowerBoundExclusive, int limit) {
            Map<String, Integer> counter = new HashMap<>();
            for (SymbolSample sample : list) {
                if (sample.timestampMs >= lowerBoundExclusive && sample.symbol != null && !sample.symbol.isBlank()) {
                    counter.merge(sample.symbol, 1, Integer::sum);
                }
            }
            return counter.entrySet().stream()
                    .sorted((left, right) -> {
                        int byCount = Integer.compare(right.getValue(), left.getValue());
                        if (byCount != 0) {
                            return byCount;
                        }
                        return left.getKey().compareTo(right.getKey());
                    })
                    .limit(Math.max(1, limit))
                    .map(entry -> new HotSymbolRecord(entry.getKey(), entry.getValue()))
                    .toList();
        }

        private static BigDecimal percent(int numerator, int denominator) {
            if (denominator <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(numerator)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        }

        private static void pruneTimes(List<Long> list, long lowerBoundExclusive) {
            Iterator<Long> iterator = list.iterator();
            while (iterator.hasNext()) {
                Long item = iterator.next();
                if (item == null || item < lowerBoundExclusive) {
                    iterator.remove();
                }
            }
        }

        private static void pruneSymbols(List<SymbolSample> list, long lowerBoundExclusive) {
            Iterator<SymbolSample> iterator = list.iterator();
            while (iterator.hasNext()) {
                SymbolSample item = iterator.next();
                if (item.timestampMs < lowerBoundExclusive) {
                    iterator.remove();
                }
            }
        }

        private static void pruneStatusSamples(List<StatusSample> list, long lowerBoundExclusive) {
            Iterator<StatusSample> iterator = list.iterator();
            while (iterator.hasNext()) {
                StatusSample item = iterator.next();
                if (item.timestampMs < lowerBoundExclusive) {
                    iterator.remove();
                }
            }
        }

        private static void pruneRiskSamples(List<RiskSample> list, long lowerBoundExclusive) {
            Iterator<RiskSample> iterator = list.iterator();
            while (iterator.hasNext()) {
                RiskSample item = iterator.next();
                if (item.timestampMs < lowerBoundExclusive) {
                    iterator.remove();
                }
            }
        }

        private static void pruneWorkflowSamples(List<WorkflowStatusSample> list, long lowerBoundExclusive) {
            Iterator<WorkflowStatusSample> iterator = list.iterator();
            while (iterator.hasNext()) {
                WorkflowStatusSample item = iterator.next();
                if (item.timestampMs < lowerBoundExclusive) {
                    iterator.remove();
                }
            }
        }

        private static boolean isOrderTerminal(String status) {
            return "filled".equals(status)
                    || "failed".equals(status)
                    || "canceled".equals(status)
                    || "cancelled".equals(status)
                    || "rejected".equals(status);
        }

        private static boolean isWorkflowTerminal(String status) {
            return "completed".equals(status)
                    || "failed".equals(status)
                    || "canceled".equals(status)
                    || "cancelled".equals(status);
        }

        private static long alignNext(long nowMs, long intervalMs) {
            long step = Math.max(1000L, intervalMs);
            long offset = nowMs % step;
            return offset == 0 ? nowMs + step : nowMs + (step - offset);
        }
    }

    static final class PostgresSnapshotSink extends RichSinkFunction<SnapshotRecord> {
        private final JobConfig config;
        private transient Connection connection;

        PostgresSnapshotSink(JobConfig config) {
            this.config = config;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            connection.setAutoCommit(false);
        }

        @Override
        public void invoke(SnapshotRecord value, Context context) throws Exception {
            if (value == null) {
                return;
            }
            long snapshotId;
            try (PreparedStatement insertSnapshot = connection.prepareStatement("""
                    INSERT INTO flink_compute_snapshot (
                        source_events_1m,
                        source_events_5m,
                        processed_events_1m,
                        processed_events_5m,
                        order_fill_rate_5m,
                        risk_reject_rate_5m,
                        avg_workflow_latency_ms_5m,
                        p95_workflow_latency_ms_5m,
                        watermark_delay_ms,
                        queued_orders_now,
                        active_runs_now,
                        completed_runs_5m,
                        failed_runs_5m,
                        computed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """)) {
                insertSnapshot.setInt(1, value.sourceEvents1m);
                insertSnapshot.setInt(2, value.sourceEvents5m);
                insertSnapshot.setInt(3, value.processedEvents1m);
                insertSnapshot.setInt(4, value.processedEvents5m);
                insertSnapshot.setBigDecimal(5, value.orderFillRate5m);
                insertSnapshot.setBigDecimal(6, value.riskRejectRate5m);
                insertSnapshot.setLong(7, value.avgWorkflowLatencyMs5m);
                insertSnapshot.setLong(8, value.p95WorkflowLatencyMs5m);
                insertSnapshot.setLong(9, value.watermarkDelayMs);
                insertSnapshot.setInt(10, value.queuedOrdersNow);
                insertSnapshot.setInt(11, value.activeRunsNow);
                insertSnapshot.setInt(12, value.completedRuns5m);
                insertSnapshot.setInt(13, value.failedRuns5m);
                insertSnapshot.setObject(14, value.computedAt);
                try (ResultSet rs = insertSnapshot.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return;
                    }
                    snapshotId = rs.getLong(1);
                }
            }

            if (value.hotSymbols != null && !value.hotSymbols.isEmpty()) {
                try (PreparedStatement insertHotSymbol = connection.prepareStatement("""
                        INSERT INTO flink_hot_symbol_snapshot (snapshot_id, symbol, order_count, rank_no)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    int rank = 1;
                    for (HotSymbolRecord row : value.hotSymbols) {
                        insertHotSymbol.setLong(1, snapshotId);
                        insertHotSymbol.setString(2, row.symbol);
                        insertHotSymbol.setInt(3, row.orderCount);
                        insertHotSymbol.setInt(4, rank);
                        insertHotSymbol.addBatch();
                        rank += 1;
                    }
                    insertHotSymbol.executeBatch();
                }
            }

            connection.commit();
        }

        @Override
        public void close() throws Exception {
            if (connection != null) {
                connection.close();
            }
        }
    }

    static final class JobConfig {
        String bootstrapServers;
        String marketTickTopic;
        String orderEventTopic;
        String riskEventTopic;
        String workflowEventTopic;
        String groupPrefix;
        long snapshotIntervalMs;
        String jdbcUrl;
        String jdbcUser;
        String jdbcPassword;

        static JobConfig fromEnv() {
            JobConfig config = new JobConfig();
            config.bootstrapServers = env("KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092");
            config.marketTickTopic = env("STREAM_TOPIC_MARKET_TICK", "aitradex.market.tick.norm");
            config.orderEventTopic = env("STREAM_TOPIC_ORDER_EVENT", "aitradex.trade.order.event");
            config.riskEventTopic = env("STREAM_TOPIC_RISK_EVENT", "aitradex.trade.risk.event");
            config.workflowEventTopic = env("STREAM_TOPIC_WORKFLOW_EVENT", "aitradex.workflow.run.event");
            config.groupPrefix = env("FLINK_GROUP_PREFIX", "aitradex-flink-compute");
            config.snapshotIntervalMs = envLong("FLINK_SNAPSHOT_INTERVAL_MS", 5000L);
            config.jdbcUrl = env("JDBC_DATABASE_URL", "jdbc:postgresql://postgres:5432/aitradex");
            config.jdbcUser = env("POSTGRES_USER", "aitradex");
            config.jdbcPassword = env("POSTGRES_PASSWORD", "aitradex");
            return config;
        }

        private static String env(String key, String defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value.trim();
        }

        private static long envLong(String key, long defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value.trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
    }

    static final class SnapshotRecord implements Serializable {
        int sourceEvents1m;
        int sourceEvents5m;
        int processedEvents1m;
        int processedEvents5m;
        BigDecimal orderFillRate5m = BigDecimal.ZERO;
        BigDecimal riskRejectRate5m = BigDecimal.ZERO;
        long avgWorkflowLatencyMs5m;
        long p95WorkflowLatencyMs5m;
        long watermarkDelayMs;
        int queuedOrdersNow;
        int activeRunsNow;
        int completedRuns5m;
        int failedRuns5m;
        OffsetDateTime computedAt;
        List<HotSymbolRecord> hotSymbols = List.of();
    }

    static final class HotSymbolRecord implements Serializable {
        String symbol;
        int orderCount;

        HotSymbolRecord(String symbol, int orderCount) {
            this.symbol = symbol;
            this.orderCount = orderCount;
        }
    }

    static final class SymbolSample implements Serializable {
        long timestampMs;
        String symbol;

        SymbolSample(long timestampMs, String symbol) {
            this.timestampMs = timestampMs;
            this.symbol = symbol;
        }
    }

    static final class StatusSample implements Serializable {
        long timestampMs;
        String status;

        StatusSample(long timestampMs, String status) {
            this.timestampMs = timestampMs;
            this.status = status;
        }
    }

    static final class RiskSample implements Serializable {
        long timestampMs;
        boolean passed;

        RiskSample(long timestampMs, boolean passed) {
            this.timestampMs = timestampMs;
            this.passed = passed;
        }
    }

    static final class WorkflowStatusSample implements Serializable {
        long timestampMs;
        String status;
        Long latencyMs;

        WorkflowStatusSample(long timestampMs, String status, Long latencyMs) {
            this.timestampMs = timestampMs;
            this.status = status;
            this.latencyMs = latencyMs;
        }
    }

    static final class AccumulatorState implements Serializable {
        List<Long> marketTimes = new ArrayList<>();
        List<SymbolSample> marketSymbols = new ArrayList<>();
        List<StatusSample> orderEvents = new ArrayList<>();
        List<RiskSample> riskEvents = new ArrayList<>();
        List<WorkflowStatusSample> workflowStatusEvents = new ArrayList<>();
        Map<String, String> latestOrderStatus = new HashMap<>();
        Map<String, String> latestWorkflowStatus = new HashMap<>();
        long lastEventTimeMs = 0L;
        long nextTimerMs = 0L;
    }
}

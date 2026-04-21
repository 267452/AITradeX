package com.aitradex.flink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlinkDecisionJob {
    private static final Logger logger = LoggerFactory.getLogger(FlinkDecisionJob.class);

    public static void main(String[] args) throws Exception {
        DecisionJobConfig config = DecisionJobConfig.fromEnv();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStream<DecisionInputEvent> marketEvents = env
                .fromSource(
                        kafkaSource(config.bootstrapServers, config.marketTickTopic, config.groupPrefix + "-market"),
                        WatermarkStrategy.noWatermarks(),
                        "decision-market-source")
                .flatMap(new MarketTickEventParser())
                .name("decision-market-parser");

        DataStream<DecisionInputEvent> riskEvents = env
                .fromSource(
                        kafkaSource(config.bootstrapServers, config.riskEventTopic, config.groupPrefix + "-risk"),
                        WatermarkStrategy.noWatermarks(),
                        "decision-risk-source")
                .flatMap(new RiskEventParser())
                .name("decision-risk-parser");

        DataStream<DecisionSignalRecord> decisionSignals = marketEvents
                .union(riskEvents)
                .keyBy(item -> 0)
                .process(new DecisionSignalFunction(config))
                .name("decision-signal-compute");

        decisionSignals.addSink(new PostgresDecisionSink(config)).name("decision-postgres-sink");

        decisionSignals
                .map(new DecisionJsonMapper())
                .sinkTo(kafkaSink(config.bootstrapServers, config.decisionTopic))
                .name("decision-kafka-sink");

        logger.info(
                "Starting flink decision job, kafka={}, marketTopic={}, riskTopic={}, decisionTopic={}, postgres={}",
                config.bootstrapServers,
                config.marketTickTopic,
                config.riskEventTopic,
                config.decisionTopic,
                config.jdbcUrl);

        env.execute("aitradex-flink-decision");
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

    private static KafkaSink<String> kafkaSink(String bootstrapServers, String topic) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                .build();
    }

    enum InputType {
        MARKET,
        RISK
    }

    static final class DecisionInputEvent implements Serializable {
        InputType type;
        long eventTimeMs;
        String symbol;
        BigDecimal lastPrice;
        Boolean riskPassed;
        String riskReason;

        static DecisionInputEvent market(long eventTimeMs, String symbol, BigDecimal lastPrice) {
            DecisionInputEvent event = new DecisionInputEvent();
            event.type = InputType.MARKET;
            event.eventTimeMs = eventTimeMs;
            event.symbol = symbol;
            event.lastPrice = lastPrice;
            return event;
        }

        static DecisionInputEvent risk(long eventTimeMs, Boolean riskPassed, String riskReason) {
            DecisionInputEvent event = new DecisionInputEvent();
            event.type = InputType.RISK;
            event.eventTimeMs = eventTimeMs;
            event.riskPassed = riskPassed;
            event.riskReason = riskReason;
            return event;
        }
    }

    static final class MarketTickEventParser implements FlatMapFunction<String, DecisionInputEvent> {
        private final ObjectMapper objectMapper;

        MarketTickEventParser() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void flatMap(String value, Collector<DecisionInputEvent> out) {
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                JsonNode node = objectMapper.readTree(value);
                String symbol = text(node.get("symbol"));
                BigDecimal lastPrice = decimal(node.get("last_price"));
                if (symbol == null || lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                out.collect(DecisionInputEvent.market(parseTime(node.get("event_time")), symbol, lastPrice));
            } catch (Exception ignored) {
                // Keep stream alive if payload is malformed.
            }
        }
    }

    static final class RiskEventParser implements FlatMapFunction<String, DecisionInputEvent> {
        private final ObjectMapper objectMapper;

        RiskEventParser() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void flatMap(String value, Collector<DecisionInputEvent> out) {
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                JsonNode node = objectMapper.readTree(value);
                JsonNode passedNode = node.get("passed");
                if (passedNode == null || passedNode.isNull()) {
                    return;
                }
                out.collect(DecisionInputEvent.risk(
                        parseTime(node.get("event_time")),
                        passedNode.asBoolean(),
                        text(node.get("reason"))));
            } catch (Exception ignored) {
                // Keep stream alive if payload is malformed.
            }
        }
    }

    static final class DecisionSignalFunction
            extends KeyedProcessFunction<Integer, DecisionInputEvent, DecisionSignalRecord> {
        private static final long RISK_WINDOW_MS = 300_000L;
        private static final long CLEAN_INTERVAL_MS = 5_000L;

        private final long decisionWindowMs;
        private final int decisionUpBps;
        private final int decisionDownBps;
        private final long decisionCooldownMs;
        private final BigDecimal maxRiskRejectRate5m;
        private transient ValueState<DecisionRuntimeState> state;

        DecisionSignalFunction(DecisionJobConfig config) {
            this.decisionWindowMs = Math.max(5_000L, config.decisionWindowSec * 1000L);
            this.decisionUpBps = Math.max(1, config.decisionUpBps);
            this.decisionDownBps = Math.max(1, config.decisionDownBps);
            this.decisionCooldownMs = Math.max(1_000L, config.decisionCooldownSec * 1000L);
            this.maxRiskRejectRate5m = config.maxRiskRejectRate5m.max(BigDecimal.ZERO);
        }

        @Override
        public void open(Configuration parameters) {
            state = getRuntimeContext().getState(new ValueStateDescriptor<>("decision-runtime-state", DecisionRuntimeState.class));
        }

        @Override
        public void processElement(DecisionInputEvent event, Context ctx, Collector<DecisionSignalRecord> out) throws Exception {
            DecisionRuntimeState runtime = state.value();
            if (runtime == null) {
                runtime = new DecisionRuntimeState();
            }

            long processingNowMs = ctx.timerService().currentProcessingTime();
            long effectiveEventTime = event.eventTimeMs <= 0 ? processingNowMs : event.eventTimeMs;

            if (event.type == InputType.RISK && event.riskPassed != null) {
                runtime.riskSamples.add(new RiskSample(effectiveEventTime, event.riskPassed));
                pruneRiskSamples(runtime.riskSamples, effectiveEventTime - RISK_WINDOW_MS);
            } else if (event.type == InputType.MARKET
                    && event.symbol != null
                    && !event.symbol.isBlank()
                    && event.lastPrice != null
                    && event.lastPrice.compareTo(BigDecimal.ZERO) > 0) {
                List<PriceSample> samples = runtime.marketSamples.computeIfAbsent(event.symbol, key -> new ArrayList<>());
                samples.add(new PriceSample(effectiveEventTime, event.lastPrice));
                prunePriceSamples(samples, effectiveEventTime - decisionWindowMs);

                if (samples.size() >= 2) {
                    PriceSample reference = samples.get(0);
                    int deltaBps = toBps(event.lastPrice, reference.price);
                    String side = deriveSide(deltaBps);
                    if (side != null && shouldEmit(runtime, event.symbol, side, effectiveEventTime)) {
                        BigDecimal rejectRate5m = computeRiskRejectRate5m(runtime.riskSamples, effectiveEventTime);
                        boolean riskGatePassed = rejectRate5m.compareTo(maxRiskRejectRate5m) <= 0;
                        if (riskGatePassed) {
                            DecisionSignalRecord signal = buildSignal(
                                    event.symbol,
                                    side,
                                    reference.price,
                                    event.lastPrice,
                                    deltaBps,
                                    rejectRate5m,
                                    effectiveEventTime,
                                    processingNowMs);
                            out.collect(signal);
                            runtime.lastDecisionBySymbol.put(event.symbol, new LastDecision(side, effectiveEventTime));
                        }
                    }
                }
            }

            pruneRuntime(runtime, effectiveEventTime, processingNowMs);
            registerNextTimer(runtime, ctx, processingNowMs);
            state.update(runtime);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<DecisionSignalRecord> out) throws Exception {
            DecisionRuntimeState runtime = state.value();
            if (runtime == null) {
                runtime = new DecisionRuntimeState();
            }
            pruneRuntime(runtime, timestamp, timestamp);
            registerNextTimer(runtime, ctx, timestamp);
            state.update(runtime);
        }

        private DecisionSignalRecord buildSignal(String symbol,
                                                 String side,
                                                 BigDecimal referencePrice,
                                                 BigDecimal triggerPrice,
                                                 int deltaBps,
                                                 BigDecimal rejectRate5m,
                                                 long eventTimeMs,
                                                 long processingNowMs) {
            DecisionSignalRecord signal = new DecisionSignalRecord();
            signal.decisionId = "dec-" + UUID.randomUUID().toString().replace("-", "");
            signal.symbol = symbol;
            signal.side = side;
            signal.confidence = confidence(deltaBps, side);
            signal.triggerPrice = triggerPrice.setScale(8, RoundingMode.HALF_UP);
            signal.referencePrice = referencePrice.setScale(8, RoundingMode.HALF_UP);
            signal.priceChangeBps = deltaBps;
            signal.windowSeconds = Math.toIntExact(decisionWindowMs / 1000L);
            signal.riskGatePassed = true;
            signal.riskRejectRate5m = rejectRate5m.setScale(4, RoundingMode.HALF_UP);
            signal.riskContext = String.format(Locale.ROOT, "risk_reject_rate_5m=%.2f%%", rejectRate5m.doubleValue());
            signal.decisionReason = "buy".equals(side) ? "momentum_breakout_up" : "momentum_breakout_down";
            signal.sourceEventTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimeMs), ZoneOffset.UTC);
            signal.computedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(processingNowMs), ZoneOffset.UTC);
            signal.decisionLatencyMs = Math.max(0L, processingNowMs - eventTimeMs);
            signal.consumed = false;
            return signal;
        }

        private String deriveSide(int deltaBps) {
            if (deltaBps >= decisionUpBps) {
                return "buy";
            }
            if (deltaBps <= -decisionDownBps) {
                return "sell";
            }
            return null;
        }

        private boolean shouldEmit(DecisionRuntimeState runtime, String symbol, String side, long eventTimeMs) {
            LastDecision last = runtime.lastDecisionBySymbol.get(symbol);
            if (last == null) {
                return true;
            }
            if (!side.equals(last.side)) {
                return true;
            }
            return eventTimeMs - last.eventTimeMs >= decisionCooldownMs;
        }

        private BigDecimal confidence(int deltaBps, String side) {
            int threshold = "buy".equals(side) ? decisionUpBps : decisionDownBps;
            double ratio = Math.abs(deltaBps) / Math.max(1.0, threshold);
            double bounded = Math.max(0.05D, Math.min(0.9999D, ratio));
            return BigDecimal.valueOf(bounded).setScale(4, RoundingMode.HALF_UP);
        }

        private BigDecimal computeRiskRejectRate5m(List<RiskSample> samples, long eventTimeMs) {
            long lowerBound = eventTimeMs - RISK_WINDOW_MS;
            int total = 0;
            int rejected = 0;
            for (RiskSample sample : samples) {
                if (sample.eventTimeMs >= lowerBound) {
                    total += 1;
                    if (!sample.passed) {
                        rejected += 1;
                    }
                }
            }
            if (total <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(rejected)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        }

        private int toBps(BigDecimal triggerPrice, BigDecimal referencePrice) {
            if (triggerPrice == null
                    || referencePrice == null
                    || referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
                return 0;
            }
            return triggerPrice.subtract(referencePrice)
                    .multiply(BigDecimal.valueOf(10_000))
                    .divide(referencePrice, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        private void pruneRuntime(DecisionRuntimeState runtime, long eventTimeMs, long nowMs) {
            pruneRiskSamples(runtime.riskSamples, eventTimeMs - RISK_WINDOW_MS);
            long marketLowerBound = eventTimeMs - (decisionWindowMs * 2);
            Iterator<Map.Entry<String, List<PriceSample>>> iterator = runtime.marketSamples.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, List<PriceSample>> entry = iterator.next();
                prunePriceSamples(entry.getValue(), marketLowerBound);
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
            long decisionLowerBound = nowMs - Math.max(60_000L, decisionCooldownMs * 10);
            runtime.lastDecisionBySymbol.entrySet().removeIf(item -> item.getValue().eventTimeMs < decisionLowerBound);
        }

        private void registerNextTimer(DecisionRuntimeState runtime, Context ctx, long nowMs) {
            long nextTimer = alignNext(nowMs, CLEAN_INTERVAL_MS);
            if (runtime.nextTimerMs <= nowMs || runtime.nextTimerMs != nextTimer) {
                ctx.timerService().registerProcessingTimeTimer(nextTimer);
                runtime.nextTimerMs = nextTimer;
            }
        }

        private static long alignNext(long nowMs, long intervalMs) {
            long safeInterval = Math.max(1_000L, intervalMs);
            long offset = nowMs % safeInterval;
            return offset == 0 ? nowMs + safeInterval : nowMs + (safeInterval - offset);
        }

        private static void prunePriceSamples(List<PriceSample> samples, long lowerBound) {
            Iterator<PriceSample> iterator = samples.iterator();
            while (iterator.hasNext()) {
                PriceSample sample = iterator.next();
                if (sample.eventTimeMs < lowerBound) {
                    iterator.remove();
                }
            }
        }

        private static void pruneRiskSamples(List<RiskSample> samples, long lowerBound) {
            Iterator<RiskSample> iterator = samples.iterator();
            while (iterator.hasNext()) {
                RiskSample sample = iterator.next();
                if (sample.eventTimeMs < lowerBound) {
                    iterator.remove();
                }
            }
        }
    }

    static final class DecisionJsonMapper extends RichMapFunction<DecisionSignalRecord, String> {
        private transient ObjectMapper objectMapper;

        @Override
        public void open(Configuration parameters) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Override
        public String map(DecisionSignalRecord value) throws Exception {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("serialize_decision_signal_failed", e);
            }
        }
    }

    static final class PostgresDecisionSink extends RichSinkFunction<DecisionSignalRecord> {
        private final DecisionJobConfig config;
        private transient Connection connection;

        PostgresDecisionSink(DecisionJobConfig config) {
            this.config = config;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            connection = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword);
            connection.setAutoCommit(false);
        }

        @Override
        public void invoke(DecisionSignalRecord value, Context context) throws Exception {
            if (value == null) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO flink_decision_signal (
                        decision_id,
                        symbol,
                        side,
                        confidence,
                        trigger_price,
                        reference_price,
                        price_change_bps,
                        window_seconds,
                        risk_gate_passed,
                        risk_reject_rate_5m,
                        risk_context,
                        decision_reason,
                        source_event_time,
                        computed_at,
                        decision_latency_ms,
                        consumed
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (decision_id) DO NOTHING
                    """)) {
                statement.setString(1, value.decisionId);
                statement.setString(2, value.symbol);
                statement.setString(3, value.side);
                statement.setBigDecimal(4, value.confidence);
                statement.setBigDecimal(5, value.triggerPrice);
                statement.setBigDecimal(6, value.referencePrice);
                statement.setInt(7, value.priceChangeBps);
                statement.setInt(8, value.windowSeconds);
                statement.setBoolean(9, value.riskGatePassed);
                statement.setBigDecimal(10, value.riskRejectRate5m);
                statement.setString(11, value.riskContext);
                statement.setString(12, value.decisionReason);
                statement.setObject(13, value.sourceEventTime);
                statement.setObject(14, value.computedAt);
                statement.setLong(15, value.decisionLatencyMs);
                statement.setBoolean(16, value.consumed);
                statement.executeUpdate();
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

    static final class DecisionJobConfig {
        String bootstrapServers;
        String marketTickTopic;
        String riskEventTopic;
        String decisionTopic;
        String groupPrefix;
        int decisionWindowSec;
        int decisionUpBps;
        int decisionDownBps;
        int decisionCooldownSec;
        BigDecimal maxRiskRejectRate5m;
        String jdbcUrl;
        String jdbcUser;
        String jdbcPassword;

        static DecisionJobConfig fromEnv() {
            DecisionJobConfig config = new DecisionJobConfig();
            config.bootstrapServers = env("KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092");
            config.marketTickTopic = env("STREAM_TOPIC_MARKET_TICK", "aitradex.market.tick.norm");
            config.riskEventTopic = env("STREAM_TOPIC_RISK_EVENT", "aitradex.trade.risk.event");
            config.decisionTopic = env("STREAM_TOPIC_DECISION_SIGNAL", "aitradex.trade.decision.signal");
            config.groupPrefix = env("FLINK_GROUP_PREFIX", "aitradex-flink-decision");
            config.decisionWindowSec = envInt("FLINK_DECISION_WINDOW_SEC", 30);
            config.decisionUpBps = envInt("FLINK_DECISION_UP_BPS", 18);
            config.decisionDownBps = envInt("FLINK_DECISION_DOWN_BPS", 18);
            config.decisionCooldownSec = envInt("FLINK_DECISION_COOLDOWN_SEC", 20);
            config.maxRiskRejectRate5m = envDecimal("FLINK_DECISION_MAX_RISK_REJECT_RATE_5M", new BigDecimal("35"));
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

        private static int envInt(String key, int defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }

        private static BigDecimal envDecimal(String key, BigDecimal defaultValue) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return new BigDecimal(value.trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
    }

    static final class DecisionRuntimeState implements Serializable {
        Map<String, List<PriceSample>> marketSamples = new HashMap<>();
        List<RiskSample> riskSamples = new ArrayList<>();
        Map<String, LastDecision> lastDecisionBySymbol = new HashMap<>();
        long nextTimerMs = 0L;
    }

    static final class PriceSample implements Serializable {
        long eventTimeMs;
        BigDecimal price;

        PriceSample(long eventTimeMs, BigDecimal price) {
            this.eventTimeMs = eventTimeMs;
            this.price = price;
        }
    }

    static final class RiskSample implements Serializable {
        long eventTimeMs;
        boolean passed;

        RiskSample(long eventTimeMs, boolean passed) {
            this.eventTimeMs = eventTimeMs;
            this.passed = passed;
        }
    }

    static final class LastDecision implements Serializable {
        String side;
        long eventTimeMs;

        LastDecision(String side, long eventTimeMs) {
            this.side = side;
            this.eventTimeMs = eventTimeMs;
        }
    }

    static final class DecisionSignalRecord implements Serializable {
        String decisionId;
        String symbol;
        String side;
        BigDecimal confidence;
        BigDecimal triggerPrice;
        BigDecimal referencePrice;
        int priceChangeBps;
        int windowSeconds;
        boolean riskGatePassed;
        BigDecimal riskRejectRate5m;
        String riskContext;
        String decisionReason;
        OffsetDateTime sourceEventTime;
        OffsetDateTime computedAt;
        long decisionLatencyMs;
        boolean consumed;
    }

    private static long parseTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return System.currentTimeMillis();
        }
        if (node.isNumber()) {
            long raw = node.asLong();
            if (raw > 0 && raw < 10_000_000_000L) {
                return raw * 1000L;
            }
            return raw;
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
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            return new BigDecimal(node.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }
}

package com.service;

import com.config.AppProperties;
import com.domain.entity.BrokerAccountEntity;
import com.util.HttpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class OkxService {
    private final BrokerAccountService brokerAccountService;
    private final FernetService fernetService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public OkxService(BrokerAccountService brokerAccountService, FernetService fernetService, HttpClient httpClient,
                      ObjectMapper objectMapper, AppProperties properties) {
        this.brokerAccountService = brokerAccountService;
        this.fernetService = fernetService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Map<String, Object> getRealData(int limit, int page, int pageSize) {
        OkxCredentials credentials = loadCredentials();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int fetchLimit = Math.min(100, Math.max(safeLimit, safePage * safePageSize));
        Map<String, Object> cfg = requestSigned(credentials, "/api/v5/account/config", Map.of());
        Map<String, Object> fills = requestSigned(credentials, "/api/v5/trade/fills-history", Map.of("instType", "SPOT", "limit", String.valueOf(fetchLimit)));
        Map<String, Object> orders = requestSigned(credentials, "/api/v5/trade/orders-history-archive", Map.of("instType", "SPOT", "limit", String.valueOf(fetchLimit)));
        ensureOk(cfg, "okx_config_error");
        ensureOk(fills, "okx_fills_error");
        ensureOk(orders, "okx_orders_error");
        Map<String, Object> cfgRow = firstRow(cfg);
        List<Map<String, Object>> fillRowsAll = rows(fills);
        List<Map<String, Object>> orderRowsAll = rows(orders);
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(start + safePageSize, orderRowsAll.size());
        List<Map<String, Object>> fillRows = start >= fillRowsAll.size() ? List.of() : fillRowsAll.subList(start, Math.min(start + safePageSize, fillRowsAll.size()));
        List<Map<String, Object>> orderRows = start >= orderRowsAll.size() ? List.of() : orderRowsAll.subList(start, end);
        return Map.of(
                "account", Map.of(
                        "uid", cfgRow.get("uid"),
                        "main_uid", cfgRow.get("mainUid"),
                        "label", cfgRow.get("label"),
                        "perm", cfgRow.get("perm"),
                        "pos_mode", cfgRow.get("posMode"),
                        "acct_lv", cfgRow.get("acctLv")),
                "fills", fillRows.stream().map(r -> Map.of(
                        "inst_id", r.get("instId"),
                        "side", r.get("side"),
                        "fill_sz", r.get("fillSz"),
                        "fill_px", r.get("fillPx"),
                        "fee", r.get("fee"),
                        "fee_ccy", r.get("feeCcy"),
                        "fill_time", toUtcIso((String) r.get("fillTime")),
                        "ord_id", r.get("ordId"),
                        "trade_id", r.get("tradeId"))).toList(),
                "orders", orderRows.stream().map(r -> Map.of(
                        "inst_id", r.get("instId"),
                        "ord_id", r.get("ordId"),
                        "side", r.get("side"),
                        "ord_type", r.get("ordType"),
                        "state", r.get("state"),
                        "sz", r.get("sz"),
                        "acc_fill_sz", r.get("accFillSz"),
                        "avg_px", r.get("avgPx"),
                        "create_time", toUtcIso((String) r.get("cTime")),
                        "update_time", toUtcIso((String) r.get("uTime")))).toList(),
                "page", safePage,
                "page_size", safePageSize,
                "has_more_fills", fillRowsAll.size() > start + safePageSize || fetchLimit == 100 && fillRowsAll.size() == 100,
                "has_more_orders", orderRowsAll.size() > start + safePageSize || fetchLimit == 100 && orderRowsAll.size() == 100,
                "visible_fill_count", fillRows.size(),
                "visible_order_count", orderRows.size(),
                "source", "okx_private_api");
    }

    public Map<String, Object> getPortfolioSnapshot(int limit) {
        OkxCredentials credentials = loadCredentials();
        Map<String, Object> cfg = requestSigned(credentials, "/api/v5/account/config", Map.of());
        Map<String, Object> bal = requestSigned(credentials, "/api/v5/account/balance", Map.of());
        ensureOk(cfg, "okx_config_error");
        ensureOk(bal, "okx_balance_error");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Map<String, Object> cfgRow = firstRow(cfg);
        Map<String, Object> balRow = firstRow(bal);
        List<Map<String, Object>> details = rowsFromAny(balRow.get("details"));
        List<Map<String, Object>> holdings = new ArrayList<>();
        for (Map<String, Object> row : details) {
            String ccy = String.valueOf(row.getOrDefault("ccy", "")).toUpperCase(Locale.ROOT);
            double cashBal = parseDouble(row.get("cashBal"));
            double eqUsd = parseDouble(row.get("eqUsd"));
            if (cashBal <= 0 && eqUsd <= 0) continue;
            holdings.add(new LinkedHashMap<>(Map.of(
                    "ccy", ccy,
                    "cash_bal", String.valueOf(row.getOrDefault("cashBal", "0")),
                    "eq_usd", String.valueOf(row.getOrDefault("eqUsd", "0")),
                    "avail_bal", String.valueOf(row.getOrDefault("availBal", "")),
                    "frozen_bal", String.valueOf(row.getOrDefault("frozenBal", "")))));
        }
        holdings.sort((a, b) -> Double.compare(parseDouble(b.get("eq_usd")), parseDouble(a.get("eq_usd"))));
        if (holdings.size() > safeLimit) holdings = new ArrayList<>(holdings.subList(0, safeLimit));
        Map<String, Object> tickers = new HashMap<>();
        double totalUsd = 0D;
        for (Map<String, Object> holding : holdings) {
            totalUsd += parseDouble(holding.get("eq_usd"));
            String ccy = String.valueOf(holding.get("ccy"));
            if (List.of("USDT", "USDC", "USD").contains(ccy)) {
                tickers.put(ccy, Map.of("inst_id", ccy + "-USDT", "last", "1", "ts", Instant.now().toString()));
                continue;
            }
            String instId = ccy + "-USDT";
            try {
                Map<String, Object> tickerResp = requestPublic(credentials.baseUrl(), "/api/v5/market/ticker", Map.of("instId", instId));
                Map<String, Object> first = firstRow(tickerResp);
                tickers.put(ccy, Map.of(
                        "inst_id", first.getOrDefault("instId", instId),
                        "last", String.valueOf(first.getOrDefault("last", "")),
                        "ts", toUtcIso(String.valueOf(first.get("ts")))));
            } catch (Exception ex) {
                tickers.put(ccy, Map.of("inst_id", instId, "last", "", "ts", null));
            }
        }
        return Map.of(
                "account", Map.of("uid", cfgRow.get("uid"), "label", cfgRow.get("label"), "perm", cfgRow.get("perm")),
                "total_eq_usd", String.format(Locale.US, "%.4f", totalUsd),
                "holdings", holdings,
                "tickers", tickers,
                "source", "okx_balance+ticker");
    }

    public OkxCredentials loadCredentials() {
        BrokerAccountEntity active = brokerAccountService.requireActiveAccountRaw();
        if (active == null) throw new IllegalStateException("active_broker_account_not_found");
        if (!"okx".equalsIgnoreCase(active.broker())) {
            throw new IllegalStateException("active_broker_account_is_not_okx");
        }
        return new OkxCredentials(
                active.baseUrl() == null || active.baseUrl().isBlank() ? "https://www.okx.com" : active.baseUrl(),
                fernetService.decrypt(active.apiKeyEncrypted()),
                fernetService.decrypt(active.apiSecretEncrypted()),
                fernetService.decrypt(active.accessTokenEncrypted()));
    }

    public Map<String, Object> requestPublic(String baseUrl, String path, Map<String, String> params) {
        return HttpUtils.getJson(httpClient, objectMapper, HttpUtils.buildUrl(baseUrl.replaceAll("/$", "") + path, params),
                Map.of("Accept", "application/json", "User-Agent", "AIBuy-Java/1.0"), properties.getGtjaQuoteTimeoutSec());
    }

    public Map<String, Object> requestSigned(OkxCredentials credentials, String path, Map<String, String> params) {
        String queryUrl = HttpUtils.buildUrl(path, params);
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        String signPayload = timestamp + "GET" + queryUrl;
        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            signature = Base64.getEncoder().encodeToString(mac.doFinal(signPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("okx_sign_failed", e);
        }
        return HttpUtils.getJson(httpClient, objectMapper, credentials.baseUrl().replaceAll("/$", "") + queryUrl,
                Map.of(
                        "OK-ACCESS-KEY", credentials.apiKey(),
                        "OK-ACCESS-SIGN", signature,
                        "OK-ACCESS-TIMESTAMP", timestamp,
                        "OK-ACCESS-PASSPHRASE", credentials.passphrase(),
                        "Content-Type", "application/json",
                        "Accept", "application/json",
                        "User-Agent", "AIBuy-Java/1.0"), properties.getGtjaQuoteTimeoutSec() + 7);
    }

    private void ensureOk(Map<String, Object> payload, String prefix) {
        if (!"0".equals(String.valueOf(payload.get("code")))) {
            throw new IllegalStateException(prefix + ":" + payload.get("code") + ":" + payload.get("msg"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> payload) {
        return rowsFromAny(payload.get("data"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsFromAny(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return List.of();
    }

    private Map<String, Object> firstRow(Map<String, Object> payload) {
        List<Map<String, Object>> rows = rows(payload);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private double parseDouble(Object raw) {
        try { return Double.parseDouble(String.valueOf(raw)); } catch (Exception e) { return 0D; }
    }

    private String toUtcIso(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return null;
        try {
            return Instant.ofEpochMilli(Long.parseLong(raw)).toString();
        } catch (Exception e) {
            return raw;
        }
    }

    public record OkxCredentials(String baseUrl, String apiKey, String secretKey, String passphrase) {}
}

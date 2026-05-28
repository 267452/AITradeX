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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class OkxService {
    private final FernetService fernetService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public OkxService(FernetService fernetService, HttpClient httpClient,
                      ObjectMapper objectMapper, AppProperties properties) {
        this.fernetService = fernetService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Map<String, Object> getAccountBalance(BrokerAccountEntity account) {
        try {
            if (!"okx".equalsIgnoreCase(account.broker())) {
                return Map.of(
                        "totalCash", 0.0,
                        "equity", 0.0,
                        "cash", 0.0,
                        "currency", "USDT",
                        "error", "Not an OKX account"
                );
            }
            
            String baseUrl = account.baseUrl() == null || account.baseUrl().isBlank() ? "https://www.okx.com" : account.baseUrl();
            String apiKey = fernetService.decrypt(account.apiKeyEncrypted());
            String apiSecret = fernetService.decrypt(account.apiSecretEncrypted());
            String passphrase = fernetService.decrypt(account.accessTokenEncrypted());
            
            OkxCredentials credentials = new OkxCredentials(baseUrl, apiKey, apiSecret, passphrase);
            
            Map<String, Object> bal = requestSigned(credentials, "/api/v5/account/balance", Map.of());
            ensureOk(bal, "okx_balance_error");
            Map<String, Object> balRow = firstRow(bal);
            List<Map<String, Object>> details = rowsFromAny(balRow.get("details"));
            
            double totalCash = 0;
            double equity = 0;
            double cash = 0;
            String currency = "USDT";
            
            for (Map<String, Object> row : details) {
                double eqUsd = parseDouble(row.get("eqUsd"));
                double availBal = parseDouble(row.get("availBal"));
                String ccy = String.valueOf(row.getOrDefault("ccy", "")).toUpperCase(Locale.ROOT);
                
                equity += eqUsd;
                if ("USDT".equals(ccy) || "USD".equals(ccy) || "USDC".equals(ccy)) {
                    cash += availBal;
                    totalCash += availBal;
                }
            }
            
            return Map.of(
                    "totalCash", totalCash,
                    "equity", equity,
                    "cash", cash,
                    "currency", currency,
                    "details", details);
        } catch (Exception e) {
            return Map.of(
                    "totalCash", 0.0,
                    "equity", 0.0,
                    "cash", 0.0,
                    "currency", "USDT",
                    "error", e.getMessage()
            );
        }
    }
    
    public Map<String, Object> getAccountPositions(BrokerAccountEntity account) {
        try {
            if (!"okx".equalsIgnoreCase(account.broker())) {
                return Map.of("positions", List.of(), "error", "Not an OKX account");
            }
            
            String baseUrl = account.baseUrl() == null || account.baseUrl().isBlank() ? "https://www.okx.com" : account.baseUrl();
            String apiKey = fernetService.decrypt(account.apiKeyEncrypted());
            String apiSecret = fernetService.decrypt(account.apiSecretEncrypted());
            String passphrase = fernetService.decrypt(account.accessTokenEncrypted());
            
            OkxCredentials credentials = new OkxCredentials(baseUrl, apiKey, apiSecret, passphrase);
            
            Map<String, Object> positionsResponse = requestSigned(credentials, "/api/v5/account/positions", Map.of("instType", "SPOT"));
            ensureOk(positionsResponse, "okx_positions_error");
            List<Map<String, Object>> positionsList = rows(positionsResponse);
            
            List<Map<String, Object>> resultPositions = new ArrayList<>();
            for (Map<String, Object> row : positionsList) {
                String instId = String.valueOf(row.getOrDefault("instId", ""));
                double quantity = parseDouble(row.get("pos"));
                if (quantity == 0) continue;
                
                double avgPx = parseDouble(row.get("avgPx"));
                double upl = parseDouble(row.get("upl"));
                double uplRatio = parseDouble(row.get("uplRatio"));
                double last = parseDouble(row.get("last"));
                double markPx = parseDouble(row.getOrDefault("markPx", last));
                
                resultPositions.add(Map.of(
                        "symbol", instId,
                        "quantity", quantity,
                        "avgCost", avgPx,
                        "lastPrice", last,
                        "marketValue", quantity * markPx,
                        "unrealizedPnl", upl,
                        "unrealizedPnlRatio", uplRatio));
            }
            
            return Map.of("positions", resultPositions);
        } catch (Exception e) {
            return Map.of("positions", List.of(), "error", e.getMessage());
        }
    }

    public Map<String, Object> requestPublic(String baseUrl, String path, Map<String, String> params) {
        return HttpUtils.getJson(httpClient, objectMapper, HttpUtils.buildUrl(baseUrl.replaceAll("/$", "") + path, params),
                Map.of("Accept", "application/json", "User-Agent", "AITradeX-Java/1.0"), properties.getGtjaQuoteTimeoutSec());
    }

    public Map<String, Object> requestSigned(OkxCredentials credentials, String path, Map<String, String> params) {
        String queryUrl = HttpUtils.buildUrl(path, params);
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        String signPayload = timestamp + "GET" + queryUrl;
        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            signature = java.util.Base64.getEncoder().encodeToString(mac.doFinal(signPayload.getBytes(StandardCharsets.UTF_8)));
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
                        "User-Agent", "AITradeX-Java/1.0"), properties.getGtjaQuoteTimeoutSec() + 7);
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
        try { return Double.parseDouble(String.valueOf(raw)); } catch (Exception e) { return 0.0; }
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

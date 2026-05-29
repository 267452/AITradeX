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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OkxService {
    private static final Logger logger = LoggerFactory.getLogger(OkxService.class);
    
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
                logger.warn("Account broker is not OKX: {}", account.broker());
                Map<String, Object> result = new HashMap<>();
                result.put("totalCash", 0.0);
                result.put("equity", 0.0);
                result.put("cash", 0.0);
                result.put("currency", "USDT");
                result.put("error", "Not an OKX account");
                return result;
            }
            
            String baseUrl = account.baseUrl() == null || account.baseUrl().isBlank() ? "https://www.okx.com" : account.baseUrl();
            
            String apiKeyEncrypted = account.apiKeyEncrypted();
            String apiSecretEncrypted = account.apiSecretEncrypted();
            String accessTokenEncrypted = account.accessTokenEncrypted();
            
            logger.info("加密凭证检查 - apiKeyEncrypted: {}, apiSecretEncrypted: {}, accessTokenEncrypted: {}", 
                       apiKeyEncrypted != null ? "exists(" + apiKeyEncrypted.length() + ")" : "null",
                       apiSecretEncrypted != null ? "exists(" + apiSecretEncrypted.length() + ")" : "null",
                       accessTokenEncrypted != null ? "exists(" + accessTokenEncrypted.length() + ")" : "null");
            
            if (apiKeyEncrypted == null || apiSecretEncrypted == null || accessTokenEncrypted == null) {
                logger.error("加密凭证包含null值!");
                Map<String, Object> result = new HashMap<>();
                result.put("totalCash", 0.0);
                result.put("equity", 0.0);
                result.put("cash", 0.0);
                result.put("currency", "USDT");
                result.put("error", "凭证解密失败: 加密字段为null");
                return result;
            }
            
            String apiKey = fernetService.decrypt(apiKeyEncrypted);
            String apiSecret = fernetService.decrypt(apiSecretEncrypted);
            String passphrase = fernetService.decrypt(accessTokenEncrypted);
            
            logger.info("解密后凭证检查 - apiKey: {}, apiSecret: {}, passphrase: {}",
                       apiKey != null ? "exists(" + apiKey.length() + ")" : "null",
                       apiSecret != null ? "exists(" + apiSecret.length() + ")" : "null",
                       passphrase != null ? "exists(" + passphrase.length() + ")" : "null");
            
            if (apiKey == null || apiSecret == null || passphrase == null) {
                logger.error("凭证解密后包含null值!");
                Map<String, Object> result = new HashMap<>();
                result.put("totalCash", 0.0);
                result.put("equity", 0.0);
                result.put("cash", 0.0);
                result.put("currency", "USDT");
                result.put("error", "凭证解密失败: 解密结果为null");
                return result;
            }
            
            logger.info("Calling OKX API for account balance, baseUrl: {}, apiKey length: {}", baseUrl, apiKey.length());
            
            OkxCredentials credentials = new OkxCredentials(baseUrl, apiKey, apiSecret, passphrase);
            
            Map<String, Object> bal = requestSigned(credentials, "/api/v5/account/balance", Map.of());
            logger.info("OKX balance API response: code={}, msg={}", bal.get("code"), bal.get("msg"));
            
            ensureOk(bal, "okx_balance_error");
            Map<String, Object> balRow = firstRow(bal);
            List<Map<String, Object>> details = rowsFromAny(balRow.get("details"));
            logger.info("Balance details count: {}", details.size());
            
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
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalCash", totalCash);
            result.put("equity", equity);
            result.put("cash", cash);
            result.put("currency", currency);
            result.put("details", details);
            logger.info("Returning balance: totalCash={}, equity={}, cash={}", totalCash, equity, cash);
            return result;
        } catch (Exception e) {
            logger.error("Error getting OKX account balance", e);
            Map<String, Object> result = new HashMap<>();
            result.put("totalCash", 0.0);
            result.put("equity", 0.0);
            result.put("cash", 0.0);
            result.put("currency", "USDT");
            result.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return result;
        }
    }
    
    public Map<String, Object> getAccountPositions(BrokerAccountEntity account) {
        try {
            if (!"okx".equalsIgnoreCase(account.broker())) {
                Map<String, Object> result = new HashMap<>();
                result.put("positions", List.of());
                result.put("error", "Not an OKX account");
                return result;
            }
            
            String baseUrl = account.baseUrl() == null || account.baseUrl().isBlank() ? "https://www.okx.com" : account.baseUrl();
            
            String apiKeyEncrypted = account.apiKeyEncrypted();
            String apiSecretEncrypted = account.apiSecretEncrypted();
            String accessTokenEncrypted = account.accessTokenEncrypted();
            
            if (apiKeyEncrypted == null || apiSecretEncrypted == null || accessTokenEncrypted == null) {
                logger.error("加密凭证包含null值!");
                Map<String, Object> result = new HashMap<>();
                result.put("positions", List.of());
                result.put("error", "凭证解密失败: 加密字段为null");
                return result;
            }
            
            String apiKey = fernetService.decrypt(apiKeyEncrypted);
            String apiSecret = fernetService.decrypt(apiSecretEncrypted);
            String passphrase = fernetService.decrypt(accessTokenEncrypted);
            
            if (apiKey == null || apiSecret == null || passphrase == null) {
                logger.error("凭证解密后包含null值!");
                Map<String, Object> result = new HashMap<>();
                result.put("positions", List.of());
                result.put("error", "凭证解密失败: 解密结果为null");
                return result;
            }
            
            logger.info("Calling OKX API for positions, baseUrl: {}", baseUrl);
            
            OkxCredentials credentials = new OkxCredentials(baseUrl, apiKey, apiSecret, passphrase);
            
            Map<String, Object> positionsResponse = requestSigned(credentials, "/api/v5/account/positions", Map.of("instType", "SPOT"));
            logger.info("OKX positions API response: code={}, msg={}", positionsResponse.get("code"), positionsResponse.get("msg"));
            
            ensureOk(positionsResponse, "okx_positions_error");
            List<Map<String, Object>> positionsList = rows(positionsResponse);
            logger.info("Positions count: {}", positionsList.size());
            
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
                
                Map<String, Object> position = new HashMap<>();
                position.put("symbol", instId);
                position.put("quantity", quantity);
                position.put("avgCost", avgPx);
                position.put("lastPrice", last);
                position.put("marketValue", quantity * markPx);
                position.put("unrealizedPnl", upl);
                position.put("unrealizedPnlRatio", uplRatio);
                resultPositions.add(position);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("positions", resultPositions);
            return result;
        } catch (Exception e) {
            logger.error("Error getting OKX positions", e);
            Map<String, Object> result = new HashMap<>();
            result.put("positions", List.of());
            result.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return result;
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
        
        // 使用HashMap替代Map.of，避免null值问题
        Map<String, String> headers = new HashMap<>();
        headers.put("OK-ACCESS-KEY", credentials.apiKey());
        headers.put("OK-ACCESS-SIGN", signature);
        headers.put("OK-ACCESS-TIMESTAMP", timestamp);
        headers.put("OK-ACCESS-PASSPHRASE", credentials.passphrase());
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "AITradeX-Java/1.0");
        
        return HttpUtils.getJson(httpClient, objectMapper, credentials.baseUrl().replaceAll("/$", "") + queryUrl,
                headers, properties.getGtjaQuoteTimeoutSec() + 7);
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

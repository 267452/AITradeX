package com.service;

import com.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private static final List<String> OKX_QUOTE_SUFFIXES = List.of("USDT", "USDC", "USD", "BTC", "ETH");
    private static final String EASTMONEY_SUGGEST_URL = "https://searchapi.eastmoney.com/api/suggest/get";
    private static final String EASTMONEY_SUGGEST_TOKEN = "D43BF722C8E33BDC906FB84D85E326E8";
    private static final Set<String> FUTURES_CLASSIFY = Set.of("CFFEX", "SHFE", "DCE", "CZCE", "INE", "GFEX");

    private static final String MARKET_CN_STOCK = "cn_stock";
    private static final String MARKET_CN_CONVERTIBLE = "cn_convertible";
    private static final String MARKET_CRYPTO = "crypto";
    private static final String MARKET_FUTURES = "futures";
    private static final String MARKET_HK_STOCK = "hk_stock";
    private static final String MARKET_US_STOCK = "us_stock";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public QuoteService(HttpClient httpClient, ObjectMapper objectMapper, AppProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String normalizeSymbol(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (s.isBlank()) return "";
        if (s.endsWith(".US") || s.contains(".")) return s;
        if (s.matches("\\d{6}")) {
            if (s.startsWith("8") || s.startsWith("4") || s.startsWith("9")) return s + ".BJ";
            return s.startsWith("6") ? s + ".SH" : s + ".SZ";
        }
        return s;
    }

    public String normalizeUsSymbol(String raw) {
        String s = normalizeSymbol(raw);
        return s.endsWith(".US") ? s.substring(0, s.length() - 3) : s;
    }

    public String normalizeOkxInstId(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT).replace("/", "-");
        if (s.startsWith("OKX:")) s = s.substring(4);
        if (s.contains("-")) return s;
        for (String suffix : OKX_QUOTE_SUFFIXES) {
            if (s.endsWith(suffix) && s.length() > suffix.length()) {
                return s.substring(0, s.length() - suffix.length()) + "-" + suffix;
            }
        }
        return s;
    }

    public boolean isOkxSymbol(String raw) {
        String s = normalizeOkxInstId(raw);
        if (!s.contains("-")) return false;
        String quote = s.substring(s.indexOf('-') + 1);
        return OKX_QUOTE_SUFFIXES.contains(quote);
    }

    public boolean isUsSymbol(String raw) {
        String s = normalizeSymbol(raw);
        return s.endsWith(".US") || s.matches("[A-Z][A-Z0-9]{0,9}(?:\\.[A-Z])?");
    }

    public boolean isCnSymbol(String raw) {
        String s = normalizeSymbol(raw);
        return s.endsWith(".SH") || s.endsWith(".SZ") || s.endsWith(".BJ") || s.matches("\\d{6}");
    }

    public Map<String, Object> getQuote(String symbol) {
        if (isOkxSymbol(symbol)) return getOkxQuote(symbol);
        if (isUsSymbol(symbol) && !isCnSymbol(symbol)) return getUsQuote(symbol);
        return getCnQuote(symbol);
    }

    public List<Map<String, Object>> searchPublicQuotes(String q, int limit, String channel) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        if ("okx".equalsIgnoreCase(channel)) return searchOkx(query, safeLimit);
        if ("us".equalsIgnoreCase(channel)) return searchUs(query, safeLimit);
        return searchCn(query, safeLimit, true);
    }

    public List<Map<String, Object>> searchPublicQuotesByMarket(String q, int limit, String market) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            throw new IllegalStateException("请输入代码或名称");
        }
        String normalizedMarket = normalizeMarket(market);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> results = switch (normalizedMarket) {
            case MARKET_CN_STOCK -> searchAStock(query, safeLimit);
            case MARKET_CN_CONVERTIBLE -> searchConvertibleBond(query, safeLimit);
            case MARKET_CRYPTO -> searchCrypto(query, safeLimit);
            case MARKET_FUTURES -> searchFutures(query, safeLimit);
            case MARKET_HK_STOCK -> searchHongKongStock(query, safeLimit);
            case MARKET_US_STOCK -> searchUsStock(query, safeLimit);
            default -> List.of();
        };
        if (results.isEmpty()) {
            throw new IllegalStateException("未在所选市场检索到匹配代码，请确认市场与代码后重试");
        }
        return results;
    }

    public Map<String, Object> getPublicKlines(String symbol, String interval, int limit) {
        int safeLimit = Math.max(10, Math.min(limit, 1200));
        String iv = interval.toLowerCase(Locale.ROOT).trim();
        if (isOkxSymbol(symbol)) {
            if ("1y".equals(iv)) {
                List<Map<String, Object>> monthly = fetchOkxKlines(symbol, "1mo", 300);
                return Map.of("provider", "okx", "symbol", normalizeOkxInstId(symbol), "interval", "1y", "items", aggregateToYear(monthly).stream().skip(Math.max(0, aggregateToYear(monthly).size() - safeLimit)).toList());
            }
            return Map.of("provider", "okx", "symbol", normalizeOkxInstId(symbol), "interval", iv, "items", fetchOkxKlines(symbol, iv, safeLimit));
        }
        if (isUsSymbol(symbol) && !isCnSymbol(symbol)) {
            if ("5m".equals(iv)) throw new IllegalStateException("kline_interval_5m_not_supported_for_usstock");
            List<Map<String, Object>> daily = fetchUsKlines(symbol, 300);
            if ("1mo".equals(iv)) return Map.of("provider", "usstock", "symbol", normalizeUsSymbol(symbol) + ".US", "interval", "1mo", "items", aggregateByMonth(daily, safeLimit));
            if ("1y".equals(iv)) return Map.of("provider", "usstock", "symbol", normalizeUsSymbol(symbol) + ".US", "interval", "1y", "items", aggregateToYear(daily).stream().skip(Math.max(0, aggregateToYear(daily).size() - safeLimit)).toList());
            return Map.of("provider", "usstock", "symbol", normalizeUsSymbol(symbol) + ".US", "interval", "1d", "items", daily.stream().skip(Math.max(0, daily.size() - safeLimit)).toList());
        }
        if ("1y".equals(iv)) {
            List<Map<String, Object>> monthly = fetchCnKlines(symbol, "1mo", 300);
            return Map.of("provider", "eastmoney", "symbol", normalizeSymbol(symbol), "interval", "1y", "items", aggregateToYear(monthly).stream().skip(Math.max(0, aggregateToYear(monthly).size() - safeLimit)).toList());
        }
        return Map.of("provider", "eastmoney", "symbol", normalizeSymbol(symbol), "interval", iv, "items", fetchCnKlines(symbol, iv, safeLimit));
    }

    private Map<String, Object> getCnQuote(String symbol) {
        String secid = secidFromSymbol(symbol);
        Map<String, Object> payload = getJson(buildUrl("https://push2.eastmoney.com/api/qt/stock/get", Map.of(
                        "invt", "2", "fltt", "2", "fields", "f43,f44,f45,f46,f57,f58", "secid", secid)),
                defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
        Map<String, Object> data = mapOf(payload.get("data"));
        Object priceRaw = data.get("f43");
        if (priceRaw == null || "-".equals(String.valueOf(priceRaw))) throw new IllegalStateException("eastmoney_quote_not_found");
        String code = String.valueOf(data.getOrDefault("f57", normalizeSymbol(symbol).substring(0, 6)));
        String market = normalizeSymbol(symbol).endsWith(".BJ") || code.startsWith("8") || code.startsWith("4") || code.startsWith("9") ? ".BJ" : (secid.startsWith("1.") ? ".SH" : ".SZ");
        return Map.of(
                "provider", "eastmoney",
                "symbol", code + market,
                "price", new BigDecimal(String.valueOf(priceRaw)),
                "bid", data.get("f45") == null || "-".equals(String.valueOf(data.get("f45"))) ? null : new BigDecimal(String.valueOf(data.get("f45"))),
                "ask", data.get("f44") == null || "-".equals(String.valueOf(data.get("f44"))) ? null : new BigDecimal(String.valueOf(data.get("f44"))),
                "ts", OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Map<String, Object> getUsQuote(String symbol) {
        String ticker = normalizeUsSymbol(symbol);
        String url = buildUrl("https://stooq.com/q/l/", Map.of("s", ticker + ".US", "i", "1"));
        String csv = SimpleHttp.get(httpClient, url, properties.getGtjaQuoteTimeoutSec());
        String[] parts = csv.trim().split(",");
        if (parts.length < 8) throw new IllegalStateException("usstock_quote_not_found");
        String price = parts[6].trim();
        if (price.isBlank() || "N/D".equals(price)) throw new IllegalStateException("usstock_quote_price_missing");
        OffsetDateTime ts = OffsetDateTime.now(ZoneOffset.UTC);
        String date = parts[1].trim();
        String time = parts[2].trim();
        try {
            if (date.length() == 8 && time.length() == 6) {
                ts = LocalDateTime.parse(date + time, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atOffset(ZoneOffset.UTC);
            }
        } catch (Exception ignored) {}
        return Map.of("provider", "usstock", "symbol", ticker + ".US", "price", new BigDecimal(price), "ts", ts);
    }

    private Map<String, Object> getOkxQuote(String symbol) {
        String instId = normalizeOkxInstId(symbol);
        Map<String, Object> payload = getJson(buildUrl("https://www.okx.com/api/v5/market/ticker", Map.of("instId", instId)),
                defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
        List<Map<String, Object>> rows = rows(payload.get("data"));
        if (rows.isEmpty()) throw new IllegalStateException("okx_quote_not_found");
        Map<String, Object> first = rows.get(0);
        String last = String.valueOf(first.getOrDefault("last", ""));
        if (last.isBlank()) throw new IllegalStateException("okx_quote_price_missing");
        OffsetDateTime ts = OffsetDateTime.now(ZoneOffset.UTC);
        try { ts = Instant.ofEpochMilli(Long.parseLong(String.valueOf(first.get("ts")))).atOffset(ZoneOffset.UTC); } catch (Exception ignored) {}
        return Map.of(
                "provider", "okx",
                "symbol", String.valueOf(first.getOrDefault("instId", instId)),
                "price", new BigDecimal(last),
                "bid", first.get("bidPx") == null || String.valueOf(first.get("bidPx")).isBlank() ? null : new BigDecimal(String.valueOf(first.get("bidPx"))),
                "ask", first.get("askPx") == null || String.valueOf(first.get("askPx")).isBlank() ? null : new BigDecimal(String.valueOf(first.get("askPx"))),
                "ts", ts);
    }

    private List<Map<String, Object>> searchCn(String query, int limit, boolean allowFallbackByCode) {
        try {
            List<Map<String, Object>> data = fetchEastmoneySuggestions(query, limit);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : data) {
                String code = String.valueOf(row.getOrDefault("Code", ""));
                if (code.isBlank()) continue;
                String securityName = String.valueOf(row.getOrDefault("SecurityTypeName", ""));
                String jys = String.valueOf(row.getOrDefault("JYS", ""));
                String mkt = String.valueOf(row.getOrDefault("MktNum", ""));
                String market = securityName.contains("京") || "81".equals(jys) || code.startsWith("8") || code.startsWith("4") || code.startsWith("9") ? ".BJ" : ("1".equals(mkt) ? ".SH" : ".SZ");
                out.add(Map.of("symbol", code + market, "name", String.valueOf(row.getOrDefault("Name", "")), "market", market));
                if (out.size() >= limit) break;
            }
            if (!out.isEmpty()) return out;
        } catch (Exception ignored) {}
        if (allowFallbackByCode && query.matches("\\d{6}")) {
            String s = normalizeSymbol(query);
            return List.of(Map.of("symbol", s, "name", "", "market", s.substring(s.length() - 3)));
        }
        return List.of();
    }

    private List<Map<String, Object>> searchUs(String query, int limit) {
        if (isUsSymbol(query) && !isCnSymbol(query)) {
            String us = normalizeUsSymbol(query);
            return List.of(Map.of("symbol", us + ".US", "name", us, "market", ".US"));
        }
        return List.of();
    }

    private List<Map<String, Object>> searchOkx(String query, int limit) {
        if (isOkxSymbol(query)) {
            String instId = normalizeOkxInstId(query);
            if (isOkxInstExists(instId)) {
                return List.of(Map.of("symbol", instId, "name", instId, "market", "区块链"));
            }
            return List.of();
        }
        try {
            Map<String, Object> payload = getJson(buildUrl("https://www.okx.com/api/v5/market/tickers", Map.of("instType", "SPOT")),
                    defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
            List<Map<String, Object>> rows = rows(payload.get("data"));
            String upper = query.toUpperCase(Locale.ROOT).replace("/", "-");
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String inst = String.valueOf(row.getOrDefault("instId", ""));
                if (!inst.isBlank() && inst.contains(upper)) {
                    out.add(Map.of("symbol", inst, "name", inst, "market", "区块链"));
                    if (out.size() >= limit) break;
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isOkxInstExists(String instId) {
        try {
            Map<String, Object> payload = getJson(buildUrl("https://www.okx.com/api/v5/market/ticker", Map.of("instId", instId)),
                    defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
            return !rows(payload.get("data")).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private List<Map<String, Object>> searchAStock(String query, int limit) {
        return searchEastmoneyByFilter(query, limit, this::isAStockRow, row -> toCnMarketItem(row, "A股"));
    }

    private List<Map<String, Object>> searchConvertibleBond(String query, int limit) {
        return searchEastmoneyByFilter(query, limit, this::isConvertibleBondRow, row -> toCnMarketItem(row, "可转债"));
    }

    private List<Map<String, Object>> searchFutures(String query, int limit) {
        return searchEastmoneyByFilter(query, limit, this::isFuturesRow, this::toFuturesItem);
    }

    private List<Map<String, Object>> searchHongKongStock(String query, int limit) {
        return searchEastmoneyByFilter(query, limit, this::isHongKongRow, this::toHongKongItem);
    }

    private List<Map<String, Object>> searchUsStock(String query, int limit) {
        return searchEastmoneyByFilter(query, limit, this::isUsStockRow, this::toUsStockItem);
    }

    private List<Map<String, Object>> searchCrypto(String query, int limit) {
        return searchOkx(query, limit);
    }

    private List<Map<String, Object>> searchEastmoneyByFilter(String query,
                                                              int limit,
                                                              Predicate<Map<String, Object>> filter,
                                                              Function<Map<String, Object>, Map<String, Object>> mapper) {
        List<Map<String, Object>> suggestions = fetchEastmoneySuggestions(query, Math.max(30, limit * 8));
        LinkedHashMap<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        for (Map<String, Object> row : suggestions) {
            if (!filter.test(row)) continue;
            Map<String, Object> mapped = mapper.apply(row);
            if (mapped == null || mapped.isEmpty()) continue;
            String symbol = String.valueOf(mapped.getOrDefault("symbol", ""));
            if (symbol.isBlank()) continue;
            deduplicated.putIfAbsent(symbol, mapped);
            if (deduplicated.size() >= limit) break;
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<Map<String, Object>> fetchEastmoneySuggestions(String query, int count) {
        try {
            Map<String, Object> payload = getJson(buildUrl(EASTMONEY_SUGGEST_URL, Map.of(
                            "input", query,
                            "type", "14",
                            "token", EASTMONEY_SUGGEST_TOKEN,
                            "count", String.valueOf(Math.max(1, Math.min(count, 100))))),
                    defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
            Map<String, Object> table = mapOf(payload.get("QuotationCodeTable"));
            return rows(table.get("Data"));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isAStockRow(Map<String, Object> row) {
        String code = stringValue(row.get("Code"));
        if (!code.matches("\\d{6}")) return false;
        String classify = upperValue(row.get("Classify"));
        String securityType = stringValue(row.get("SecurityTypeName"));
        return "ASTOCK".equals(classify)
                || securityType.contains("沪A")
                || securityType.contains("深A")
                || securityType.contains("京A")
                || securityType.contains("科创板")
                || securityType.contains("创业板");
    }

    private boolean isConvertibleBondRow(Map<String, Object> row) {
        String code = stringValue(row.get("Code"));
        if (!code.matches("\\d{6}")) return false;
        String classify = upperValue(row.get("Classify"));
        String securityType = stringValue(row.get("SecurityTypeName"));
        String name = stringValue(row.get("Name"));
        boolean bondLike = "BOND".equals(classify) || securityType.contains("债");
        boolean convertibleLike = name.contains("转债") || code.startsWith("11") || code.startsWith("12");
        return bondLike && convertibleLike;
    }

    private boolean isFuturesRow(Map<String, Object> row) {
        String code = stringValue(row.get("Code"));
        if (code.isBlank()) return false;
        String classify = upperValue(row.get("Classify"));
        String securityType = stringValue(row.get("SecurityTypeName"));
        return securityType.contains("期货") || FUTURES_CLASSIFY.contains(classify);
    }

    private boolean isHongKongRow(Map<String, Object> row) {
        String code = stringValue(row.get("Code"));
        if (code.isBlank()) return false;
        String classify = upperValue(row.get("Classify"));
        String securityType = stringValue(row.get("SecurityTypeName"));
        String mktNum = stringValue(row.get("MktNum"));
        return "HK".equals(classify) || securityType.contains("港股") || "116".equals(mktNum);
    }

    private boolean isUsStockRow(Map<String, Object> row) {
        String code = stringValue(row.get("Code"));
        if (code.isBlank()) return false;
        String classify = upperValue(row.get("Classify"));
        String securityType = stringValue(row.get("SecurityTypeName"));
        String mktNum = stringValue(row.get("MktNum"));
        return "USSTOCK".equals(classify) || securityType.contains("美股") || "105".equals(mktNum) || "106".equals(mktNum) || "107".equals(mktNum);
    }

    private Map<String, Object> toCnMarketItem(Map<String, Object> row, String marketLabel) {
        String code = stringValue(row.get("Code"));
        if (!code.matches("\\d{6}")) return Map.of();
        return Map.of(
                "symbol", cnSymbol(code, row),
                "name", stringValue(row.get("Name")),
                "market", marketLabel);
    }

    private Map<String, Object> toFuturesItem(Map<String, Object> row) {
        String code = stringValue(row.get("Code")).toUpperCase(Locale.ROOT);
        if (code.isBlank()) return Map.of();
        return Map.of(
                "symbol", code,
                "name", stringValue(row.get("Name")),
                "market", "期货");
    }

    private Map<String, Object> toHongKongItem(Map<String, Object> row) {
        String codeRaw = stringValue(row.get("Code")).toUpperCase(Locale.ROOT);
        if (codeRaw.isBlank()) return Map.of();
        String code = codeRaw;
        if (code.matches("\\d+")) {
            try {
                code = String.format(Locale.ROOT, "%05d", Integer.parseInt(code));
            } catch (NumberFormatException ignored) {}
        }
        String symbol = code.endsWith(".HK") ? code : code + ".HK";
        return Map.of(
                "symbol", symbol,
                "name", stringValue(row.get("Name")),
                "market", "港股");
    }

    private Map<String, Object> toUsStockItem(Map<String, Object> row) {
        String code = stringValue(row.get("Code")).toUpperCase(Locale.ROOT).replace("/", ".");
        if (code.isBlank()) return Map.of();
        String symbol = code.endsWith(".US") ? code : code + ".US";
        return Map.of(
                "symbol", symbol,
                "name", stringValue(row.get("Name")),
                "market", "美股");
    }

    private String cnSymbol(String code, Map<String, Object> row) {
        String securityName = stringValue(row.get("SecurityTypeName"));
        String jys = stringValue(row.get("JYS"));
        String mkt = stringValue(row.get("MktNum"));
        if (securityName.contains("京") || "81".equals(jys) || code.startsWith("8") || code.startsWith("4") || code.startsWith("9")) {
            return code + ".BJ";
        }
        return "1".equals(mkt) ? code + ".SH" : code + ".SZ";
    }

    private String normalizeMarket(String rawMarket) {
        String market = rawMarket == null ? "" : rawMarket.trim().toLowerCase(Locale.ROOT);
        if (market.isBlank()) {
            throw new IllegalStateException("请选择检索市场");
        }
        return switch (market) {
            case "cn_stock", "a_share", "ashare", "a", "astock", "cn", "stock" -> MARKET_CN_STOCK;
            case "cn_convertible", "convertible", "bond", "convertible_bond", "kzz" -> MARKET_CN_CONVERTIBLE;
            case "crypto", "chain", "blockchain", "okx" -> MARKET_CRYPTO;
            case "futures", "future", "qh" -> MARKET_FUTURES;
            case "hk_stock", "hk", "hongkong" -> MARKET_HK_STOCK;
            case "us_stock", "us", "usstock" -> MARKET_US_STOCK;
            default -> throw new IllegalStateException("不支持的市场类型: " + rawMarket);
        };
    }

    private List<Map<String, Object>> fetchCnKlines(String symbol, String interval, int limit) {
        String secid = secidFromSymbol(symbol);
        String klt = switch (interval.toLowerCase(Locale.ROOT)) { case "5m" -> "5"; case "1mo" -> "103"; default -> "101"; };
        Map<String, Object> payload = getJson(buildUrl("https://push2his.eastmoney.com/api/qt/stock/kline/get", Map.of(
                        "secid", secid, "klt", klt, "fqt", "1", "lmt", String.valueOf(limit), "end", "20500101",
                        "fields1", "f1,f2,f3,f4,f5,f6", "fields2", "f51,f52,f53,f54,f55,f56,f57,f58")),
                defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
        Map<String, Object> data = mapOf(payload.get("data"));
        List<Object> klines = data.get("klines") instanceof List<?> list ? new ArrayList<>(list) : List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object line : klines) {
            String[] p = String.valueOf(line).split(",");
            if (p.length < 6) continue;
            out.add(Map.of("ts", p[0], "open", p[1], "high", p[3], "low", p[4], "close", p[2], "volume", p[5]));
        }
        return out;
    }

    private List<Map<String, Object>> fetchOkxKlines(String symbol, String interval, int limit) {
        String instId = normalizeOkxInstId(symbol);
        String bar = switch (interval.toLowerCase(Locale.ROOT)) { case "5m" -> "5m"; case "1mo" -> "1M"; default -> "1D"; };
        int target = Math.max(10, Math.min(limit, 1200));
        List<List<Object>> rowsAll = new ArrayList<>();
        String after = null;
        int rounds = 0;
        while (rowsAll.size() < target && rounds < 8) {
            rounds += 1;
            int batch = Math.min(300, target - rowsAll.size());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("instId", instId);
            params.put("bar", bar);
            params.put("limit", String.valueOf(batch));
            if (after != null) params.put("after", after);
            Map<String, Object> payload = getJson(buildUrl("https://www.okx.com/api/v5/market/history-candles", params), defaultHeaders(), properties.getGtjaQuoteTimeoutSec());
            List<?> data = payload.get("data") instanceof List<?> list ? list : List.of();
            if (data.isEmpty()) break;
            for (Object item : data) {
                if (item instanceof List<?> list) rowsAll.add(new ArrayList<>(list));
            }
            List<Object> last = rowsAll.get(rowsAll.size() - 1);
            after = String.valueOf(last.get(0));
            if (data.size() < batch) break;
        }
        Map<String, List<Object>> unique = new LinkedHashMap<>();
        rowsAll.stream().sorted((a, b) -> String.valueOf(a.get(0)).compareTo(String.valueOf(b.get(0)))).forEach(row -> unique.put(String.valueOf(row.get(0)), row));
        List<List<Object>> rows = new ArrayList<>(unique.values());
        if (rows.size() > target) rows = rows.subList(rows.size() - target, rows.size());
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<Object> row : rows) {
            if (row.size() < 6) continue;
            out.add(Map.of(
                    "ts", Instant.ofEpochMilli(Long.parseLong(String.valueOf(row.get(0)))).toString(),
                    "open", String.valueOf(row.get(1)),
                    "high", String.valueOf(row.get(2)),
                    "low", String.valueOf(row.get(3)),
                    "close", String.valueOf(row.get(4)),
                    "volume", String.valueOf(row.get(5))));
        }
        return out;
    }

    private List<Map<String, Object>> fetchUsKlines(String symbol, int limit) {
        String url = buildUrl("https://stooq.com/q/d/l/", Map.of("s", normalizeUsSymbol(symbol).toLowerCase(Locale.ROOT) + ".us", "i", "d"));
        String csv = SimpleHttp.get(httpClient, url, properties.getGtjaQuoteTimeoutSec());
        List<Map<String, Object>> out = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new StringReader(csv))) {
            String header = reader.readLine();
            if (header == null) return List.of();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 6) continue;
                out.add(Map.of("ts", p[0], "open", p[1], "high", p[2], "low", p[3], "close", p[4], "volume", p.length > 5 ? p[5] : ""));
            }
        } catch (Exception ignored) {}
        if (out.size() > limit) return out.subList(out.size() - limit, out.size());
        return out;
    }

    private List<Map<String, Object>> aggregateByMonth(List<Map<String, Object>> rows, int limit) {
        LinkedHashMap<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String ts = String.valueOf(row.get("ts"));
            if (ts.length() < 7) continue;
            String key = ts.substring(0, 7);
            if (!grouped.containsKey(key)) {
                grouped.put(key, new LinkedHashMap<>(Map.of("ts", key, "open", row.get("open"), "high", row.get("high"), "low", row.get("low"), "close", row.get("close"), "volume", row.get("volume"))));
            } else {
                Map<String, Object> g = grouped.get(key);
                g.put("high", String.valueOf(Math.max(Double.parseDouble(String.valueOf(g.get("high"))), Double.parseDouble(String.valueOf(row.get("high"))))));
                g.put("low", String.valueOf(Math.min(Double.parseDouble(String.valueOf(g.get("low"))), Double.parseDouble(String.valueOf(row.get("low"))))));
                g.put("close", row.get("close"));
                g.put("volume", String.valueOf(Double.parseDouble(String.valueOf(g.get("volume"))) + Double.parseDouble(String.valueOf(row.get("volume")))));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        if (result.size() > limit) return result.subList(result.size() - limit, result.size());
        return result;
    }

    private List<Map<String, Object>> aggregateToYear(List<Map<String, Object>> rows) {
        LinkedHashMap<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String ts = String.valueOf(row.get("ts"));
            if (ts.length() < 4) continue;
            String year = ts.substring(0, 4);
            if (!grouped.containsKey(year)) {
                grouped.put(year, new LinkedHashMap<>(Map.of("ts", year, "open", row.get("open"), "high", row.get("high"), "low", row.get("low"), "close", row.get("close"), "volume", row.get("volume"))));
            } else {
                Map<String, Object> g = grouped.get(year);
                g.put("high", String.valueOf(Math.max(Double.parseDouble(String.valueOf(g.get("high"))), Double.parseDouble(String.valueOf(row.get("high"))))));
                g.put("low", String.valueOf(Math.min(Double.parseDouble(String.valueOf(g.get("low"))), Double.parseDouble(String.valueOf(row.get("low"))))));
                g.put("close", row.get("close"));
                g.put("volume", String.valueOf(Double.parseDouble(String.valueOf(g.get("volume"))) + Double.parseDouble(String.valueOf(row.get("volume")))));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private String secidFromSymbol(String symbol) {
        String s = normalizeSymbol(symbol);
        if (s.endsWith(".SH")) return "1." + s.substring(0, 6);
        if (s.endsWith(".SZ") || s.endsWith(".BJ")) return "0." + s.substring(0, 6);
        if (s.matches("\\d{6}")) return s.startsWith("6") ? "1." + s : "0." + s;
        throw new IllegalStateException("unsupported_symbol_format");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
            return out;
        }
        return List.of();
    }

    private Map<String, String> defaultHeaders() {
        return Map.of("Accept", "application/json", "User-Agent", "Mozilla/5.0 (AIBuy-Java/1.0)");
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String upperValue(Object raw) {
        return stringValue(raw).toUpperCase(Locale.ROOT);
    }

    private String buildUrl(String base, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return base;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return base + "?" + joiner;
    }

    private Map<String, Object> getJson(String url, Map<String, String> headers, int timeoutSec) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .GET();
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("http_error:" + response.statusCode());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("network_error", e);
        } catch (IOException e) {
            throw new IllegalStateException("network_error", e);
        }
    }

    static class SimpleHttp {
        static String get(HttpClient client, String url, int timeoutSec) {
            try {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(timeoutSec)).GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) throw new IllegalStateException("http_error:" + response.statusCode());
                return response.body();
            } catch (Exception e) {
                throw new IllegalStateException("network_error", e);
            }
        }
    }
}

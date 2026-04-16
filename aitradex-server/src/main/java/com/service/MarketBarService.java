package com.service;

import com.domain.request.ImportCsvRequest;
import com.domain.request.SimulateBarsRequest;
import com.repository.MarketDataRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class MarketBarService {
    private final MarketDataRepository marketDataRepository;
    private final Random random = new Random();

    public MarketBarService(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    public Map<String, Object> importCsv(ImportCsvRequest req) throws IOException {
        int inserted = 0;
        int skipped = 0;
        List<String> lines = Files.readAllLines(java.nio.file.Path.of(req.csvPath()), StandardCharsets.UTF_8);
        int start = req.hasHeader() ? 1 : 0;
        for (int i = start; i < lines.size(); i++) {
            try {
                String[] p = lines.get(i).split(",");
                boolean ok = marketDataRepository.insertMarketBar(
                        p[0],
                        req.timeframe(),
                        parseDateTime(p[1]),
                        new BigDecimal(p[2]),
                        new BigDecimal(p[3]),
                        new BigDecimal(p[4]),
                        new BigDecimal(p[5]),
                        new BigDecimal(p[6]));
                if (ok) {
                    inserted++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                skipped++;
            }
        }
        return Map.of("inserted", inserted, "skipped", skipped);
    }

    public Map<String, Object> simulateBars(SimulateBarsRequest req) {
        int inserted = 0;
        BigDecimal price = req.startPrice();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < req.bars(); i++) {
            BigDecimal step = BigDecimal.valueOf(-0.015 + (0.03 * random.nextDouble()));
            BigDecimal close = price.multiply(BigDecimal.ONE.add(step)).max(new BigDecimal("0.01"));
            BigDecimal high = price.max(close).multiply(new BigDecimal("1.002"));
            BigDecimal low = price.min(close).multiply(new BigDecimal("0.998"));
            BigDecimal volume = BigDecimal.valueOf(1000 + random.nextInt(49000));
            OffsetDateTime barTime = "1d".equals(req.timeframe()) ? now.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(i) : now.withSecond(0).withNano(0).plusMinutes(i);
            if (marketDataRepository.insertMarketBar(req.symbol(), req.timeframe(), barTime, price, high, low, close, volume)) {
                inserted++;
            }
            price = close;
        }
        return Map.of("inserted", inserted, "symbol", req.symbol());
    }

    private OffsetDateTime parseDateTime(String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return java.time.LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

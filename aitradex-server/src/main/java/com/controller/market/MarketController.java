package com.controller.market;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import com.config.AppProperties;
import com.domain.request.ImportCsvRequest;
import com.domain.request.MarketTickIngestItem;
import com.domain.request.MarketTickIngestRequest;
import com.domain.request.SimulateBarsRequest;
import com.domain.stream.MarketTickStreamEvent;
import com.service.MarketBarService;
import com.service.QuoteService;
import com.service.StreamEventPublisher;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private final QuoteService quoteService;
    private final MarketBarService marketBarService;
    private final StreamEventPublisher streamEventPublisher;
    private final AppProperties properties;

    public MarketController(QuoteService quoteService,
                            MarketBarService marketBarService,
                            StreamEventPublisher streamEventPublisher,
                            AppProperties properties) {
        this.quoteService = quoteService;
        this.marketBarService = marketBarService;
        this.streamEventPublisher = streamEventPublisher;
        this.properties = properties;
    }

    @GetMapping("/quote/search")
    public ApiResponse<List<Map<String, Object>>> marketQuoteSearch(@RequestParam String q,
                                                                     @RequestParam String market,
                                                                     @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(quoteService.searchPublicQuotesByMarket(q, limit, market));
    }

    @GetMapping("/quote/{symbol}")
    public ApiResponse<Map<String, Object>> marketQuote(@PathVariable String symbol) {
        return ApiResponse.success(quoteService.getQuote(symbol));
    }

    @GetMapping("/kline/{symbol}")
    public ApiResponse<Map<String, Object>> marketKline(@PathVariable String symbol,
                                                        @RequestParam(defaultValue = "1m") String interval,
                                                        @RequestParam(defaultValue = "120") int limit) {
        return ApiResponse.success(quoteService.getPublicKlines(symbol, interval, limit));
    }

    @PostMapping("/bars/import-csv")
    public ApiResponse<Map<String, Object>> importCsv(@RequestBody ImportCsvRequest req) throws IOException {
        return ApiResponse.success(marketBarService.importCsv(req));
    }

    @PostMapping("/bars/simulate")
    public ApiResponse<Map<String, Object>> simulateBars(@RequestBody SimulateBarsRequest req) {
        return ApiResponse.success(marketBarService.simulateBars(req));
    }

    @PostMapping("/ticks/ingest")
    public ApiResponse<Map<String, Object>> ingestTicks(@RequestBody MarketTickIngestRequest req,
                                                        @RequestHeader(value = "X-Stream-Token", required = false) String streamToken) {
        if (!properties.isStreamEnabled()) {
            throw new BusinessException(400, "stream_not_enabled");
        }
        String expectedToken = properties.getStreamIngestToken();
        if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(streamToken)) {
            throw new BusinessException(401, "stream_ingest_token_invalid");
        }
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new BusinessException(400, "stream_ingest_items_required");
        }
        int accepted = 0;
        OffsetDateTime recvTime = OffsetDateTime.now(ZoneOffset.UTC);
        for (MarketTickIngestItem item : req.items()) {
            if (item == null || item.symbol() == null || item.symbol().isBlank() || item.lastPrice() == null) {
                continue;
            }
            String symbol = quoteService.normalizeSymbol(item.symbol());
            OffsetDateTime eventTime = item.eventTime() == null ? recvTime : item.eventTime();
            String sourceEventId = item.sourceEventId() == null || item.sourceEventId().isBlank()
                    ? "ingest-" + UUID.randomUUID()
                    : item.sourceEventId();
            streamEventPublisher.publishMarketTick(MarketTickStreamEvent.of(
                    item.source() == null || item.source().isBlank() ? "external" : item.source(),
                    item.exchange() == null || item.exchange().isBlank() ? "unknown" : item.exchange(),
                    symbol,
                    eventTime,
                    recvTime,
                    item.lastPrice(),
                    item.bid1(),
                    item.ask1(),
                    item.volume(),
                    item.turnover(),
                    sourceEventId));
            accepted += 1;
        }
        return ApiResponse.success(Map.of("accepted", accepted, "received", req.items().size()));
    }
}

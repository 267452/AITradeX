package com.controller.market;

import com.common.api.ApiResponse;
import com.domain.request.ImportCsvRequest;
import com.domain.request.SimulateBarsRequest;
import com.service.MarketBarService;
import com.service.QuoteService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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

    public MarketController(QuoteService quoteService, MarketBarService marketBarService) {
        this.quoteService = quoteService;
        this.marketBarService = marketBarService;
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
}

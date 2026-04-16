package com.controller.trade;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import com.domain.entity.OrderEntity;
import com.domain.request.BacktestRequest;
import com.domain.response.OrderDetailResponse;
import com.domain.request.SignalRequest;
import com.domain.request.StrategyRunRequest;
import com.domain.request.TradeCommandRequest;
import com.domain.response.SignalProcessResponse;
import com.domain.response.StrategySignalResult;
import com.domain.response.TradeCommandParseResult;
import com.repository.TradeRepository;
import com.service.BacktestService;
import com.service.QuoteService;
import com.service.TradeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
public class TradeController {
    private final TradeService tradeService;
    private final QuoteService quoteService;
    private final TradeRepository tradeRepository;
    private final BacktestService backtestService;

    public TradeController(TradeService tradeService, QuoteService quoteService,
                           TradeRepository tradeRepository, BacktestService backtestService) {
        this.tradeService = tradeService;
        this.quoteService = quoteService;
        this.tradeRepository = tradeRepository;
        this.backtestService = backtestService;
    }

    @PostMapping("/signals")
    public ApiResponse<SignalProcessResponse> createSignal(@RequestBody SignalRequest signal) {
        return ApiResponse.success(tradeService.processSignal(signal));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable long orderId) {
        OrderEntity row = tradeRepository.getOrderById(orderId);
        if (row == null) {
            throw new BusinessException(404, "order_not_found");
        }
        return ApiResponse.success(new OrderDetailResponse(
                row.id(),
                row.symbol(),
                row.side(),
                row.orderType(),
                row.price(),
                row.quantity(),
                row.status(),
                row.strategyName()));
    }

    @PostMapping("/trade/command")
    public ApiResponse<Object> tradeCommand(@RequestBody TradeCommandRequest req) {
        TradeCommandParseResult parsed = tradeService.parseTradeCommand(req);
        if (!parsed.ok()) {
            return ApiResponse.success(parsed);
        }
        if (req.dryRun()) {
            return ApiResponse.success(new TradeCommandParseResult(true, parsed.action(), "dry_run_ok", parsed.symbol(), parsed.side(), parsed.quantity(), parsed.strategyName()));
        }
        String action = parsed.action();
        if ("manual_signal".equals(action)) {
            Map<String, Object> quote = quoteService.getQuote(parsed.symbol());
            SignalRequest signal = tradeService.buildManualSignal((java.math.BigDecimal) quote.get("price"), parsed.side(), parsed.symbol(), parsed.quantity(), parsed.strategyName());
            SignalProcessResponse out = tradeService.processSignal(signal);
            if (!out.riskPassed()) {
                return ApiResponse.success(Map.of("ok", false, "action", action, "message", out.message()));
            }
            return ApiResponse.success(Map.of("ok", true, "action", action, "message", "order_queued", "symbol", parsed.symbol(), "side", parsed.side(), "quantity", parsed.quantity(), "strategy_name", parsed.strategyName(), "order_id", out.orderId()));
        }
        if ("strategy_run".equals(action)) {
            StrategyRunRequest sreq = tradeService.buildStrategyRequest(parsed.symbol(), parsed.quantity(), parsed.strategyName());
            StrategySignalResult generated = tradeService.generateSignalFromSma(sreq);
            if (generated.signal() == null) {
                return ApiResponse.success(Map.of("ok", false, "action", action, "message", generated.reason(), "symbol", parsed.symbol()));
            }
            SignalProcessResponse out = tradeService.processSignal(generated.signal());
            if (!out.riskPassed()) {
                return ApiResponse.success(Map.of("ok", false, "action", action, "message", out.message(), "symbol", parsed.symbol()));
            }
            return ApiResponse.success(Map.of("ok", true, "action", action, "message", "order_queued", "symbol", parsed.symbol(), "side", generated.signal().side(), "quantity", parsed.quantity(), "strategy_name", sreq.strategyName(), "order_id", out.orderId()));
        }
        return ApiResponse.success(Map.of("ok", false, "action", "unknown", "message", "unsupported_command"));
    }

    @PostMapping("/strategy/run")
    public ApiResponse<Map<String, Object>> runStrategy(@RequestBody StrategyRunRequest req) {
        StrategySignalResult generated = tradeService.generateSignalFromSma(req);
        if (generated.signal() == null) {
            return ApiResponse.success(Map.of("symbol", req.symbol(), "signal_generated", false, "reason", generated.reason()));
        }
        SignalProcessResponse out = tradeService.processSignal(generated.signal());
        return ApiResponse.success(Map.of("symbol", req.symbol(), "signal_generated", out.riskPassed(), "reason", out.message(), "order_id", out.orderId()));
    }

    @PostMapping("/backtest/sma")
    public ApiResponse<Map<String, Object>> backtest(@RequestBody BacktestRequest req) {
        return ApiResponse.success(backtestService.runSmaBacktest(req));
    }

    @GetMapping("/backtest/reports")
    public ApiResponse<java.util.List<Map<String, Object>>> backtestReports(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(backtestService.listReports(limit));
    }
}

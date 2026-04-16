package com.controller.broker;

import com.common.api.ApiResponse;
import com.common.exception.BusinessException;
import com.config.AppProperties;
import com.domain.request.BrokerAccountCreateRequest;
import com.domain.request.BrokerSwitchRequest;
import com.domain.response.BrokerAccountResponse;
import com.domain.response.BrokerModeResponse;
import com.repository.SystemSettingRepository;
import com.service.BrokerAccountService;
import com.service.BrokerService;
import com.service.OkxService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broker")
public class BrokerController {
    private final BrokerService brokerService;
    private final BrokerAccountService brokerAccountService;
    private final OkxService okxService;
    private final SystemSettingRepository systemSettingRepository;
    private final AppProperties properties;

    public BrokerController(BrokerService brokerService, BrokerAccountService brokerAccountService,
                            OkxService okxService, SystemSettingRepository systemSettingRepository,
                            AppProperties properties) {
        this.brokerService = brokerService;
        this.brokerAccountService = brokerAccountService;
        this.okxService = okxService;
        this.systemSettingRepository = systemSettingRepository;
        this.properties = properties;
    }

    @GetMapping("/mode")
    public ApiResponse<BrokerModeResponse> brokerMode() {
        Map<String, Object> info = brokerService.currentBrokerInfo();
        return ApiResponse.success(new BrokerModeResponse(String.valueOf(info.get("broker")), String.valueOf(info.get("source")), properties.getBrokerMode()));
    }

    @PostMapping("/switch")
    public ApiResponse<Map<String, Object>> brokerSwitch(@RequestBody BrokerSwitchRequest req) {
        String broker = req.broker().trim().toLowerCase(Locale.ROOT);
        if (!List.of("paper", "gtja", "real", "okx", "usstock").contains(broker)) {
            throw new BusinessException("unsupported_broker");
        }
        systemSettingRepository.upsertSystemSetting("active_broker", broker);
        return ApiResponse.success(Map.of("broker", broker, "source", "system_setting"));
    }

    @PostMapping("/accounts")
    public ApiResponse<BrokerAccountResponse> createBrokerAccount(@RequestBody BrokerAccountCreateRequest req) {
        return ApiResponse.success(brokerAccountService.addAccount(req));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<BrokerAccountResponse>> listBrokerAccounts(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(brokerAccountService.listAccounts(limit));
    }

    @PostMapping("/accounts/{accountId}/activate")
    public ApiResponse<BrokerAccountResponse> activateBrokerAccount(@PathVariable long accountId) {
        BrokerAccountResponse row = brokerAccountService.activate(accountId);
        if (row == null) {
            throw new BusinessException(404, "broker_account_not_found");
        }
        return ApiResponse.success(row);
    }

    @GetMapping("/accounts/active")
    public ApiResponse<BrokerAccountResponse> activeBrokerAccount() {
        BrokerAccountResponse row = brokerAccountService.activeAccount();
        if (row == null) {
            throw new BusinessException(404, "active_broker_account_not_found");
        }
        return ApiResponse.success(row);
    }

    @GetMapping("/okx/real-data")
    public ApiResponse<Map<String, Object>> okxRealData(@RequestParam(defaultValue = "10") int limit,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(okxService.getRealData(limit, page, pageSize));
    }

    @GetMapping("/okx/portfolio")
    public ApiResponse<Map<String, Object>> okxPortfolio(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(okxService.getPortfolioSnapshot(limit));
    }
}

package com.service;

import com.domain.entity.BrokerAccountEntity;
import com.domain.request.BrokerAccountCreateRequest;
import com.domain.request.BrokerAccountUpdateRequest;
import com.domain.response.BrokerAccountResponse;
import com.repository.BrokerAccountRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BrokerAccountService {
    private final BrokerAccountRepository brokerAccountRepository;
    private final FernetService fernetService;
    private final OkxService okxService;

    public BrokerAccountService(BrokerAccountRepository brokerAccountRepository, FernetService fernetService, OkxService okxService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.fernetService = fernetService;
        this.okxService = okxService;
    }

    public BrokerAccountResponse addAccount(BrokerAccountCreateRequest req) {
        BrokerAccountEntity row = brokerAccountRepository.createBrokerAccount(
                req.broker(),
                req.accountName(),
                req.baseUrl(),
                fernetService.encrypt(req.apiKey()),
                fernetService.encrypt(req.apiSecret()),
                fernetService.encrypt(req.accessToken()));
        return toBrokerAccountOut(row);
    }

    public BrokerAccountResponse getAccount(long accountId) {
        BrokerAccountEntity row = brokerAccountRepository.getBrokerAccount(accountId);
        return row == null ? null : toBrokerAccountOut(row);
    }

    public BrokerAccountResponse updateAccount(long accountId, BrokerAccountUpdateRequest req) {
        BrokerAccountEntity existingRow = brokerAccountRepository.getBrokerAccount(accountId);
        if (existingRow == null) {
            return null;
        }
        
        BrokerAccountEntity row = brokerAccountRepository.updateBrokerAccount(
                accountId,
                req.broker() != null ? req.broker() : existingRow.broker(),
                req.accountName() != null ? req.accountName() : existingRow.accountName(),
                req.baseUrl() != null ? req.baseUrl() : existingRow.baseUrl(),
                req.apiKey() != null ? fernetService.encrypt(req.apiKey()) : existingRow.apiKeyEncrypted(),
                req.apiSecret() != null ? fernetService.encrypt(req.apiSecret()) : existingRow.apiSecretEncrypted(),
                req.accessToken() != null ? fernetService.encrypt(req.accessToken()) : existingRow.accessTokenEncrypted());
        return toBrokerAccountOut(row);
    }

    public boolean deleteAccount(long accountId) {
        return brokerAccountRepository.deleteBrokerAccount(accountId);
    }

    public List<BrokerAccountResponse> listAccounts(int limit) {
        return brokerAccountRepository.listBrokerAccounts(limit).stream().map(this::toBrokerAccountOut).toList();
    }

    public BrokerAccountResponse activate(long accountId) {
        BrokerAccountEntity row = brokerAccountRepository.activateBrokerAccount(accountId);
        return row == null ? null : toBrokerAccountOut(row);
    }

    public BrokerAccountResponse activeAccount() {
        BrokerAccountEntity row = brokerAccountRepository.getActiveBrokerAccount();
        return row == null ? null : toBrokerAccountOut(row);
    }

    public BrokerAccountEntity requireActiveAccountRaw() {
        return brokerAccountRepository.getActiveBrokerAccount();
    }

    public Map<String, Object> getAccountBalance(long accountId) {
        BrokerAccountEntity account = brokerAccountRepository.getBrokerAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        
        if ("okx".equalsIgnoreCase(account.broker())) {
            return okxService.getAccountBalance(account);
        }
        
        String currency = "usstock".equalsIgnoreCase(account.broker()) ? "USD" : "CNY";
        return Map.of(
                "totalCash", 0.0,
                "equity", 0.0,
                "cash", 0.0,
                "currency", currency);
    }

    public Map<String, Object> getAccountPositions(long accountId) {
        BrokerAccountEntity account = brokerAccountRepository.getBrokerAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        
        if ("okx".equalsIgnoreCase(account.broker())) {
            return okxService.getAccountPositions(account);
        }
        
        return Map.of("positions", List.of());
    }

    private BrokerAccountResponse toBrokerAccountOut(BrokerAccountEntity row) {
        String apiKey = fernetService.decrypt(row.apiKeyEncrypted());
        return new BrokerAccountResponse(
                row.id(),
                row.broker(),
                row.accountName(),
                row.baseUrl(),
                mask(apiKey),
                Boolean.TRUE.equals(row.enabled()),
                Boolean.TRUE.equals(row.active()),
                row.createdAt());
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 6) return "*".repeat(value.length());
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}
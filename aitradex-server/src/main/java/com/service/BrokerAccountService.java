package com.service;

import com.domain.entity.BrokerAccountEntity;
import com.domain.request.BrokerAccountCreateRequest;
import com.domain.response.BrokerAccountResponse;
import com.repository.BrokerAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BrokerAccountService {
    private final BrokerAccountRepository brokerAccountRepository;
    private final FernetService fernetService;

    public BrokerAccountService(BrokerAccountRepository brokerAccountRepository, FernetService fernetService) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.fernetService = fernetService;
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

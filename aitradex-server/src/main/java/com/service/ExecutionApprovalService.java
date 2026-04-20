package com.service;

import com.config.AppProperties;
import com.domain.request.AiConfirmExecuteRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExecutionApprovalService {
    private static final String SHA_256 = "SHA-256";

    private final AppProperties properties;
    private final BrokerService brokerService;

    public ExecutionApprovalService(AppProperties properties, BrokerService brokerService) {
        this.properties = properties;
        this.brokerService = brokerService;
    }

    public boolean isLiveBrokerMode() {
        String broker = String.valueOf(brokerService.currentBrokerInfo().getOrDefault("broker", "paper"))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (broker.isEmpty()) {
            return false;
        }
        return !("paper".equals(broker) || "sim".equals(broker) || "mock".equals(broker));
    }

    public ApprovalCheckResult validateLiveConfirm(AiConfirmExecuteRequest request, String operator) {
        String normalizedOperator = normalizeUser(operator, "unknown_operator");
        boolean liveMode = isLiveBrokerMode();

        if (!liveMode) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("approval_required", false);
            context.put("live_mode", false);
            context.put("operator", normalizedOperator);
            context.put("approved", true);
            return new ApprovalCheckResult(true, "ok", context);
        }

        String configuredPassphrase = safeTrim(properties.getExecutionApprovalPassphrase());
        if (configuredPassphrase.isEmpty()) {
            return new ApprovalCheckResult(
                    false,
                    "实盘审批口令未配置，请设置 APP_EXECUTION_APPROVAL_PASSPHRASE 后再执行",
                    Map.of("approval_required", true, "live_mode", true));
        }

        String providedPassphrase = safeTrim(request.approvalPassphrase());
        if (providedPassphrase.isEmpty()) {
            return new ApprovalCheckResult(
                    false,
                    "实盘执行需要审批口令",
                    Map.of("approval_required", true, "live_mode", true));
        }
        if (!secureEquals(configuredPassphrase, providedPassphrase)) {
            return new ApprovalCheckResult(
                    false,
                    "审批口令不正确，执行已阻止",
                    Map.of("approval_required", true, "live_mode", true));
        }

        String coApprover = normalizeUser(request.coApprover(), "");
        if (coApprover.isEmpty()) {
            return new ApprovalCheckResult(
                    false,
                    "实盘执行需要填写复核人",
                    Map.of("approval_required", true, "live_mode", true));
        }
        if (coApprover.equalsIgnoreCase(normalizedOperator)) {
            return new ApprovalCheckResult(
                    false,
                    "复核人不能与当前操作人相同",
                    Map.of("approval_required", true, "live_mode", true));
        }

        String approvalNote = safeTrim(request.approvalNote());
        OffsetDateTime approvedAt = OffsetDateTime.now();
        String signature = digestHex(
                normalizedOperator + "|" + coApprover + "|" + safeTrim(request.command()) + "|" + approvedAt.toString())
                .substring(0, 24);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("approval_required", true);
        context.put("live_mode", true);
        context.put("approved", true);
        context.put("approved_at", approvedAt);
        context.put("approval_signature", signature);
        context.put("operator", normalizedOperator);
        context.put("co_approver", coApprover);
        context.put("approval_note", approvalNote);
        return new ApprovalCheckResult(true, "ok", context);
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeUser(String value, String fallback) {
        String normalized = safeTrim(value);
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized;
    }

    private String safeTrim(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String digestHex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte aByte : bytes) {
                builder.append(String.format("%02x", aByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha256_not_supported", e);
        }
    }

    public record ApprovalCheckResult(boolean approved, String message, Map<String, Object> context) {
    }
}


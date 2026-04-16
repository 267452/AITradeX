package com.domain.response;

import com.domain.request.SignalRequest;

public record StrategySignalResult(SignalRequest signal, String reason) {
}

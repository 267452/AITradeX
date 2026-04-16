package com.domain.entity;

import java.math.BigDecimal;

public record OrderEntity(
        Long id,
        String symbol,
        String side,
        String orderType,
        BigDecimal price,
        Integer quantity,
        String status,
        String strategyName) {
}

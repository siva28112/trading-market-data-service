package com.acme.trading.marketdata.model;

import java.math.BigDecimal;

public record OrderBookLevel(
        BigDecimal price,
        BigDecimal size
) {}

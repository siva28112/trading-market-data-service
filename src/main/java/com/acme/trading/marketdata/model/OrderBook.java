package com.acme.trading.marketdata.model;

import java.time.Instant;
import java.util.List;

public record OrderBook(
        String symbol,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Instant timestamp
) {}

package com.acme.trading.marketdata.service;

import com.acme.trading.domain.InstrumentType;
import com.acme.trading.dto.InstrumentMetadata;
import com.acme.trading.dto.Quote;
import com.acme.trading.exception.TradingException;
import com.acme.trading.marketdata.model.OrderBook;
import com.acme.trading.marketdata.model.OrderBookLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QuoteService {

    private static final Map<String, Quote> QUOTES = Map.of(
            "AAPL", quote("AAPL", "189.50", "189.55", "189.52", "45000000"),
            "MSFT", quote("MSFT", "415.20", "415.30", "415.25", "22000000"),
            "GOOG", quote("GOOG", "175.80", "175.90", "175.85", "18000000")
    );

    private static final Map<String, InstrumentMetadata> INSTRUMENTS = Map.of(
            "AAPL", metadata("AAPL", "Apple Inc.", InstrumentType.EQUITY),
            "MSFT", metadata("MSFT", "Microsoft Corporation", InstrumentType.EQUITY),
            "GOOG", metadata("GOOG", "Alphabet Inc. Class C", InstrumentType.EQUITY)
    );

    public Quote getQuote(String symbol) {
        Quote quote = QUOTES.get(normalize(symbol));
        if (quote == null) {
            throw new TradingException("Quote not found for symbol: " + symbol);
        }
        return refreshTimestamp(quote);
    }

    public List<Quote> getBatchQuotes(List<String> symbols) {
        return symbols.stream()
                .map(this::getQuote)
                .collect(Collectors.toList());
    }

    public OrderBook getOrderBook(String symbol) {
        Quote quote = getQuote(symbol);
        return new OrderBook(
                quote.symbol(),
                List.of(
                        new OrderBookLevel(quote.bid(), bd("500")),
                        new OrderBookLevel(quote.bid().subtract(bd("0.05")), bd("1200"))
                ),
                List.of(
                        new OrderBookLevel(quote.ask(), bd("600")),
                        new OrderBookLevel(quote.ask().add(bd("0.05")), bd("900"))
                ),
                Instant.now()
        );
    }

    public List<InstrumentMetadata> listInstruments() {
        return List.copyOf(INSTRUMENTS.values());
    }

    public InstrumentMetadata getMetadata(String symbol) {
        InstrumentMetadata metadata = INSTRUMENTS.get(normalize(symbol));
        if (metadata == null) {
            throw new TradingException("Instrument not found: " + symbol);
        }
        return metadata;
    }

    private Quote refreshTimestamp(Quote quote) {
        return new Quote(quote.symbol(), quote.bid(), quote.ask(), quote.last(), quote.volume(), Instant.now());
    }

    private static Quote quote(String symbol, String bid, String ask, String last, String volume) {
        return new Quote(symbol, bd(bid), bd(ask), bd(last), bd(volume), Instant.now());
    }

    private static InstrumentMetadata metadata(String symbol, String name, InstrumentType type) {
        return new InstrumentMetadata(
                symbol, name, type, "NASDAQ", Currency.getInstance("USD"), bd("0.01"), bd("1"), true
        );
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

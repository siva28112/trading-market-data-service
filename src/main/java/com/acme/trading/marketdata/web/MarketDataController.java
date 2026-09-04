package com.acme.trading.marketdata.web;

import com.acme.trading.dto.InstrumentMetadata;
import com.acme.trading.dto.Quote;
import com.acme.trading.exception.TradingException;
import com.acme.trading.marketdata.model.OrderBook;
import com.acme.trading.marketdata.service.QuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MarketDataController {

    private final QuoteService quoteService;

    public MarketDataController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/quotes/{symbol}")
    public Quote getQuote(@PathVariable String symbol) {
        return quoteService.getQuote(symbol);
    }

    @PostMapping("/quotes/batch")
    public List<Quote> getBatchQuotes(@RequestBody List<String> symbols) {
        return quoteService.getBatchQuotes(symbols);
    }

    @GetMapping("/orderbook/{symbol}")
    public OrderBook getOrderBook(@PathVariable String symbol) {
        return quoteService.getOrderBook(symbol);
    }

    @GetMapping("/instruments")
    public List<InstrumentMetadata> listInstruments() {
        return quoteService.listInstruments();
    }

    @GetMapping("/instruments/{symbol}/metadata")
    public InstrumentMetadata getMetadata(@PathVariable String symbol) {
        return quoteService.getMetadata(symbol);
    }
}

@RestController
class MarketDataExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(TradingException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(TradingException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }
}

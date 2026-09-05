package com.acme.trading.marketdata.service;

import com.acme.trading.domain.InstrumentType;
import com.acme.trading.dto.InstrumentMetadata;
import com.acme.trading.dto.Quote;
import com.acme.trading.exception.TradingException;
import com.acme.trading.marketdata.model.OrderBook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteServiceTest {

    private final QuoteService service = new QuoteService();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    class Quotes {

        @Test
        void aKnownSymbolReturnsItsSeededQuote() {
            Quote quote = service.getQuote("AAPL");

            assertEquals("AAPL", quote.symbol());
            assertEquals(bd("189.50"), quote.bid());
            assertEquals(bd("189.55"), quote.ask());
            assertEquals(bd("189.52"), quote.last());
            assertEquals(bd("45000000"), quote.volume());
        }

        @ParameterizedTest(name = "symbol [{0}] resolves to AAPL")
        @ValueSource(strings = {"AAPL", "aapl", " aapl ", "AaPl", "\tAAPL\n"})
        void symbolIsTrimmedAndUpperCased(String symbol) {
            assertEquals("AAPL", service.getQuote(symbol).symbol());
        }

        @Test
        void anUnknownSymbolIsRejectedWithTheSymbolAsGiven() {
            TradingException thrown =
                    assertThrows(TradingException.class, () -> service.getQuote("nosuch"));

            // The message echoes the caller's spelling, not the normalised form.
            assertEquals("Quote not found for symbol: nosuch", thrown.getMessage());
        }

        @Test
        void aNullSymbolFailsFastRatherThanReturningNothing() {
            assertThrows(NullPointerException.class, () -> service.getQuote(null));
        }

        @Test
        void theTimestampIsRefreshedOnEveryReadWhilePricesStayFixed() throws InterruptedException {
            Quote first = service.getQuote("MSFT");
            Thread.sleep(2);
            Quote second = service.getQuote("MSFT");

            assertNotEquals(first.timestamp(), second.timestamp(),
                    "each read should be stamped with the time it was served");
            assertEquals(first.bid(), second.bid());
            assertEquals(first.ask(), second.ask());
            assertEquals(first.last(), second.last());
        }
    }

    @Nested
    class BatchQuotes {

        @Test
        void everyRequestedSymbolIsReturnedInOrder() {
            List<Quote> quotes = service.getBatchQuotes(List.of("AAPL", "MSFT", "GOOG"));

            assertEquals(List.of("AAPL", "MSFT", "GOOG"), quotes.stream().map(Quote::symbol).toList());
        }

        @Test
        void mixedCaseSymbolsAreNormalisedInABatch() {
            assertEquals(List.of("AAPL", "GOOG"),
                    service.getBatchQuotes(List.of("aapl", " goog ")).stream().map(Quote::symbol).toList());
        }

        @Test
        void anEmptyBatchReturnsAnEmptyList() {
            assertEquals(List.of(), service.getBatchQuotes(List.of()));
        }

        @Test
        void oneUnknownSymbolFailsTheWholeBatch() {
            // Documents current behaviour: the batch is all-or-nothing. There is no
            // partial result and no per-symbol error entry.
            TradingException thrown = assertThrows(TradingException.class,
                    () -> service.getBatchQuotes(List.of("AAPL", "NOSUCH", "MSFT")));

            assertEquals("Quote not found for symbol: NOSUCH", thrown.getMessage());
        }

        @Test
        void duplicateSymbolsAreReturnedOncePerRequest() {
            assertEquals(3, service.getBatchQuotes(List.of("AAPL", "AAPL", "AAPL")).size());
        }
    }

    @Nested
    class OrderBooks {

        @Test
        void theBookIsBuiltAroundTheQuotedBidAndAsk() {
            OrderBook book = service.getOrderBook("AAPL");

            assertEquals("AAPL", book.symbol());
            assertEquals(2, book.bids().size());
            assertEquals(2, book.asks().size());

            // Bids step down from the quoted bid by 5 cents; asks step up from the ask.
            assertEquals(bd("189.50"), book.bids().get(0).price());
            assertEquals(bd("189.45"), book.bids().get(1).price());
            assertEquals(bd("189.55"), book.asks().get(0).price());
            assertEquals(bd("189.60"), book.asks().get(1).price());
        }

        @Test
        void levelSizesAreTheSeededDepth() {
            OrderBook book = service.getOrderBook("AAPL");

            assertEquals(bd("500"), book.bids().get(0).size());
            assertEquals(bd("1200"), book.bids().get(1).size());
            assertEquals(bd("600"), book.asks().get(0).size());
            assertEquals(bd("900"), book.asks().get(1).size());
        }

        @Test
        void theBookIsCrossedNowhereBestBidBelowBestAsk() {
            OrderBook book = service.getOrderBook("MSFT");

            assertTrue(book.bids().get(0).price().compareTo(book.asks().get(0).price()) < 0,
                    "best bid must be below best ask");
        }

        @Test
        void anUnknownSymbolIsRejected() {
            assertThrows(TradingException.class, () -> service.getOrderBook("NOSUCH"));
        }

        @Test
        void symbolIsNormalisedForTheBookToo() {
            assertEquals("GOOG", service.getOrderBook(" goog ").symbol());
        }
    }

    @Nested
    class Instruments {

        @Test
        void allSeededInstrumentsAreListed() {
            List<InstrumentMetadata> instruments = service.listInstruments();

            assertEquals(3, instruments.size());
            assertEquals(List.of("AAPL", "GOOG", "MSFT"),
                    instruments.stream().map(InstrumentMetadata::symbol).sorted().toList());
        }

        @Test
        void theInstrumentListIsAnImmutableCopy() {
            List<InstrumentMetadata> instruments = service.listInstruments();

            assertThrows(UnsupportedOperationException.class, () -> instruments.remove(0));
        }

        @Test
        void metadataCarriesTheFullReferenceRecord() {
            InstrumentMetadata metadata = service.getMetadata("AAPL");

            assertEquals("AAPL", metadata.symbol());
            assertEquals("Apple Inc.", metadata.name());
            assertEquals(InstrumentType.EQUITY, metadata.instrumentType());
            assertEquals("NASDAQ", metadata.exchange());
            assertEquals(Currency.getInstance("USD"), metadata.currency());
            assertEquals(bd("0.01"), metadata.tickSize());
            assertEquals(bd("1"), metadata.lotSize());
            assertTrue(metadata.tradable());
        }

        @ParameterizedTest(name = "metadata lookup for [{0}] resolves to MSFT")
        @ValueSource(strings = {"MSFT", "msft", " msft "})
        void metadataLookupIsNormalised(String symbol) {
            assertEquals("Microsoft Corporation", service.getMetadata(symbol).name());
        }

        @Test
        void unknownInstrumentIsRejectedWithItsOwnMessage() {
            TradingException thrown =
                    assertThrows(TradingException.class, () -> service.getMetadata("NOSUCH"));

            assertEquals("Instrument not found: NOSUCH", thrown.getMessage());
        }
    }
}

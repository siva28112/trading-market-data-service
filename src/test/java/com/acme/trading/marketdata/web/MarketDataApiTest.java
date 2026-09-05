package com.acme.trading.marketdata.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the HTTP contract against a running server. MockMvc rethrows handler
 * exceptions, which would hide what the container returns for the error cases below.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketDataApiTest {

    @Autowired
    private TestRestTemplate rest;

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("GET /api/v1/quotes/{symbol} returns the quote, normalising the symbol")
    void getQuote() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/quotes/aapl", JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode quote = response.getBody();
        assertEquals("AAPL", quote.get("symbol").asText());
        // compareTo, not equals: the JsonNode here is a DoubleNode, so decimalValue()
        // has already lost the scale. See trailingZerosSurviveOnTheWireButNotIntoJsonNode.
        assertEquals(0, new BigDecimal("189.50").compareTo(quote.get("bid").decimalValue()));
        assertEquals(0, new BigDecimal("189.55").compareTo(quote.get("ask").decimalValue()));
        assertEquals(0, new BigDecimal("189.52").compareTo(quote.get("last").decimalValue()));
    }

    @Test
    @DisplayName("POST /api/v1/quotes/batch returns one quote per requested symbol, in order")
    void batchQuotes() {
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/quotes/batch", HttpMethod.POST,
                json("[\"AAPL\",\"msft\"]"), JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("AAPL", response.getBody().get(0).get("symbol").asText());
        assertEquals("MSFT", response.getBody().get(1).get("symbol").asText());
    }

    @Test
    @DisplayName("GET /api/v1/orderbook/{symbol} returns two levels a side, uncrossed")
    void orderBook() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/orderbook/AAPL", JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode book = response.getBody();
        assertEquals("AAPL", book.get("symbol").asText());
        assertEquals(2, book.get("bids").size());
        assertEquals(2, book.get("asks").size());
        assertEquals(0, new BigDecimal("189.50").compareTo(book.get("bids").get(0).get("price").decimalValue()));
        assertEquals(0, new BigDecimal("189.45").compareTo(book.get("bids").get(1).get("price").decimalValue()));
        assertEquals(0, new BigDecimal("189.55").compareTo(book.get("asks").get(0).get("price").decimalValue()));
        assertEquals(0, new BigDecimal("189.60").compareTo(book.get("asks").get(1).get("price").decimalValue()));
        assertTrue(book.get("bids").get(0).get("price").decimalValue()
                        .compareTo(book.get("asks").get(0).get("price").decimalValue()) < 0,
                "best bid must be below best ask");
    }

    @Test
    @DisplayName("GET /api/v1/instruments lists every seeded instrument")
    void listInstruments() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/instruments", JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
    }

    @Test
    @DisplayName("GET /api/v1/instruments/{symbol}/metadata returns the reference record")
    void instrumentMetadata() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("/api/v1/instruments/msft/metadata", JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode metadata = response.getBody();
        assertEquals("MSFT", metadata.get("symbol").asText());
        assertEquals("Microsoft Corporation", metadata.get("name").asText());
        assertEquals("EQUITY", metadata.get("instrumentType").asText());
        assertEquals("NASDAQ", metadata.get("exchange").asText());
        assertTrue(metadata.get("tradable").asBoolean());
    }

    @Test
    @DisplayName("BigDecimal scale survives serialisation but is lost by a JsonNode client")
    void trailingZerosSurviveOnTheWireButNotIntoJsonNode() {
        ResponseEntity<String> raw = rest.getForEntity("/api/v1/quotes/AAPL", String.class);

        // The server emits the seeded scale verbatim: "bid":189.50, not 189.5.
        assertTrue(raw.getBody().contains("\"bid\":189.50"),
                "server should emit the BigDecimal scale, got: " + raw.getBody());

        // But a client deserialising into a generic JsonNode gets a DoubleNode, because
        // Jackson only produces DecimalNode with USE_BIG_DECIMAL_FOR_FLOATS enabled. The
        // scale is gone before the caller sees it, so assert prices with compareTo.
        ResponseEntity<JsonNode> parsed = rest.getForEntity("/api/v1/quotes/AAPL", JsonNode.class);
        assertEquals(new BigDecimal("189.5"), parsed.getBody().get("bid").decimalValue());
        assertEquals(0, new BigDecimal("189.50").compareTo(parsed.getBody().get("bid").decimalValue()));
    }

    // -------------------------------------------------------------------------------------
    // Error contract. Records what the service does TODAY, which is not what it should do.
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unknown symbol returns 500, not 404 (MarketDataExceptionHandler never fires)")
    void unknownSymbolReturnsServerErrorNotNotFound() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/quotes/NOSUCH", JsonNode.class);

        // MarketDataExceptionHandler in MarketDataController.java is annotated
        // @RestController rather than @ControllerAdvice, so its @ExceptionHandler is
        // scoped to a class that declares no request mappings and never fires.
        // TradingException escapes and Boot's default handler returns 500.
        // The handler itself is written to return 404 with {"error": "..."}.
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    @DisplayName("an unknown instrument returns 500, not 404 (same root cause)")
    void unknownInstrumentReturnsServerError() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("/api/v1/instruments/NOSUCH/metadata", JsonNode.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    @DisplayName("one bad symbol fails the whole batch with 500")
    void oneBadSymbolFailsTheWholeBatch() {
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/quotes/batch", HttpMethod.POST,
                json("[\"AAPL\",\"NOSUCH\"]"), JsonNode.class);

        // The batch is all-or-nothing: no partial results, no per-symbol error entries.
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}

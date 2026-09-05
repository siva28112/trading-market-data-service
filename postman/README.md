# Postman collections

`trading-market-data-service.postman_collection.json` covers all five endpoints plus
the error contract.

## Run it

```bash
mvn -q package -DskipTests
java -jar target/trading-market-data-service-1.0.0.jar
npx newman run postman/trading-market-data-service.postman_collection.json
```

Requests are independent — the service holds seeded read-only data (AAPL, MSFT, GOOG),
so there is no state to chain and nothing is written. Override `baseUrl` and `symbol`
with `--env-var` to point elsewhere.

## The "Error contract" folder

Records what the service returns **today**, not what it should. `MarketDataExceptionHandler`
in `MarketDataController.java` is annotated `@RestController` rather than
`@ControllerAdvice`, so its `@ExceptionHandler` is scoped to a class with no request
mappings and never fires. The handler is written to return 404 with `{"error": "..."}`,
but `TradingException` escapes to Boot's default handler and the caller gets 500.

Fixing the annotation makes those assertions fail loudly; update them to 404 then.

## Last verified

```
requests     9
assertions  25
failed       0
```

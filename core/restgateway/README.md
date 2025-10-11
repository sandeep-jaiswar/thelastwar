# REST Gateway

Reactive, non-blocking REST API layer for The Last War trading system.

## Overview

The REST Gateway provides high-performance REST endpoints for external order submissions, cancellations, and status queries. Built on Spring WebFlux with Reactor Netty, it offers:

- **Non-blocking I/O**: Fully reactive architecture using Spring WebFlux
- **High throughput**: Target ≥ 10,000 requests/second
- **Low latency**: End-to-end latency ≤ 2 ms at p99
- **JWT Authentication**: Secure token-based authentication
- **Rate Limiting**: 10,000 req/sec per user using token bucket algorithm
- **EventBus Integration**: Asynchronous order dispatch via internal EventBus
- **Ultra-fast serialization**: DSL-JSON for < 10 µs JSON operations

⚠️ **Important Production Consideration**: The current implementation uses in-memory state for order storage. This means **order data is lost on application restart**. For production deployments, integrate with Redis, a distributed cache, or a database for persistent order state. See the "Known Limitations" section below for details.

## Architecture

```
┌──────────────┐
│   REST API   │  (Spring WebFlux - Reactor Netty)
└──────┬───────┘
       │
       ├─ JWT Authentication Filter
       ├─ Rate Limiting Filter (Bucket4j)
       │
       ▼
┌──────────────┐
│ OrderService │  (Business Logic)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  EventBus    │  (Async Dispatch - Aeron IPC)
└──────────────┘
```

## API Endpoints

### Submit Order
```http
POST /api/orders
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "symbol": "AAPL",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 100,
  "price": 15000,
  "account": 999
}
```

**Request Fields:**
- `symbol` (string, required): Trading symbol/instrument (e.g., "AAPL", "MSFT")
- `side` (string, required): Order side - "BUY" or "SELL"
- `orderType` (string, required): Order type - "MARKET", "LIMIT", "STOP", or "STOP_LIMIT"
- `quantity` (long, required): Order quantity in shares/lots (must be positive)
- `price` (long, required): Price in minimum increments (0 for market orders)
- `account` (long, required): Trading account identifier (numeric ID)

**Response:**
```json
{
  "success": true,
  "message": "Order submitted successfully",
  "data": 12345,
  "timestamp": 1699564800000
}
```

### Get Order Status
```http
GET /api/orders/{id}
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": 12345,
    "symbol": "AAPL",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 100,
    "price": 15000,
    "status": "NEW",
    "timestamp": 1699564800000
  },
  "timestamp": 1699564800000
}
```

### Cancel Order
```http
POST /api/orders/{id}/cancel
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "message": "Order cancelled successfully",
  "data": true,
  "timestamp": 1699564800000
}
```

### Health Check
```http
GET /health
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "1699564800000"
}
```

## Authentication

The REST Gateway uses JWT (JSON Web Tokens) for authentication. To obtain a token, implement an authentication service that issues tokens using the same secret key.

**Example JWT Token Generation:**
```java
JwtTokenProvider tokenProvider = new JwtTokenProvider();
String token = tokenProvider.createToken("username");
```

**Using the token:**
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Rate Limiting

The gateway implements rate limiting using the token bucket algorithm (Bucket4j):
- **Capacity**: 10,000 requests
- **Refill rate**: 10,000 requests per second
- **Per user**: Each authenticated user has their own bucket

When the rate limit is exceeded, the API returns HTTP 429 (Too Many Requests).

## Running the Gateway

### Using Gradle

```bash
# Build the module
./gradlew :core:restgateway:build

# Run tests
./gradlew :core:restgateway:test

# Run as Spring Boot application
./gradlew :core:restgateway:bootRun
```

### Configuration

Edit `application.properties` to customize:

```properties
# Server port
server.port=8080

# Reactor Netty workers
spring.reactor.netty.ioWorkerCount=4

# Logging level
logging.level.com.thelastwar=DEBUG
```

## Performance Testing

### Unit Tests
```bash
./gradlew :core:restgateway:test
```

### JMH Benchmarks
```bash
./gradlew :core:restgateway:jmh
```

Expected benchmark results:
- **Order submission**: < 2 ms average latency
- **Order validation**: < 10 µs
- **Throughput**: ≥ 10,000 orders/sec

### Load Testing with wrk

```bash
# Install wrk
sudo apt-get install wrk

# Run throughput test (requires running server)
wrk -t4 -c100 -d30s --latency \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -s order_submit.lua \
  http://localhost:8080/api/orders
```

**order_submit.lua:**
```lua
wrk.method = "POST"
wrk.body   = '{"symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"price":15000,"account":999}'
wrk.headers["Content-Type"] = "application/json"
```

## Integration with EventBus

Orders submitted through the REST Gateway are published to the internal EventBus as events:

```java
Event event = Event.now(
    orderId,
    SourceId.REST_GATEWAY,
    EventType.ORDER_SUBMITTED,
    0L,
    orderEvent
);
eventBus.publish(event);
```

Downstream services (Matching Engine, Risk Manager) subscribe to these events for processing.

## Error Handling

The API returns structured error responses with appropriate HTTP status codes:

**Validation Error (400 Bad Request):**
```json
{
  "success": false,
  "message": "Symbol is required",
  "data": null,
  "timestamp": 1699564800000
}
```

Common error scenarios:
- **Invalid request**: HTTP 400 Bad Request with detailed error message
- **Authentication failure**: HTTP 401 Unauthorized
- **Rate limit exceeded**: HTTP 429 Too Many Requests
- **Order not found**: HTTP 404 Not Found
- **Server error**: HTTP 500 Internal Server Error

**Note**: Currently, validation errors return HTTP 200 with `success: false` for backward compatibility. This will be changed to proper 4xx status codes in a future release.

## Dependencies

Key dependencies:
- **Spring Boot 3.2.2**: Framework foundation
- **Spring WebFlux**: Reactive web layer
- **Reactor Netty**: High-performance HTTP server
- **DSL-JSON 2.0.2**: Ultra-fast JSON serialization
- **JJWT 0.12.5**: JWT token handling
- **Bucket4j 8.7.0**: Rate limiting
- **Micrometer**: Metrics collection

## Security Considerations

1. **JWT Secret**: Use a secure, randomly generated secret in production
2. **HTTPS**: Always use HTTPS in production
3. **Token Expiration**: Tokens expire after 1 hour (configurable)
4. **Input Validation**: All inputs are validated before processing
5. **Rate Limiting**: Prevents abuse and DoS attacks

## Monitoring

The gateway integrates with Micrometer for metrics:
- Request throughput
- Latency percentiles (p50, p95, p99)
- Error rates
- Active connections

Metrics can be exposed via Prometheus or other monitoring systems.

## Known Limitations

⚠️ **Critical Production Considerations:**

1. **In-Memory State (HIGH PRIORITY)**: Order state is stored in-memory using ConcurrentHashMap
   - **Impact**: All order data is lost when the application restarts
   - **Implication**: No order persistence, no disaster recovery, no horizontal scaling
   - **Solution**: Replace with Redis, Hazelcast, or a persistent database
   - **Example**: Use Spring Data Redis with RedisTemplate or ReactiveRedisTemplate
   ```java
   // Production-ready approach
   @Autowired
   private ReactiveRedisTemplate<String, OrderEvent> redisTemplate;
   
   public Mono<OrderEvent> getOrder(long orderId) {
       return redisTemplate.opsForValue().get("order:" + orderId);
   }
   ```

2. **No WebSocket Support**: No real-time updates for order status changes
   - **Solution**: Add WebSocket endpoints for streaming updates

3. **Single Node Limitation**: No horizontal scalability without external state store
   - **Solution**: Add session affinity or use distributed state management

4. **Basic Authentication**: Only JWT, no OAuth2/OIDC support
   - **Solution**: Integrate Spring Security OAuth2

5. **No API Versioning**: Single API version, breaking changes affect all clients
   - **Solution**: Add `/v1/` prefix and implement versioning strategy

6. **Status Code Inconsistency**: Validation errors currently return 200 with `success: false`
   - **Solution**: Return proper 4xx status codes (400 for validation errors)

## Future Enhancements

- [ ] WebSocket support for real-time updates
- [ ] GraphQL API for complex queries
- [ ] API versioning
- [ ] Request/response caching
- [ ] Circuit breaker pattern
- [ ] Distributed tracing (OpenTelemetry)
- [ ] OAuth2/OIDC support
- [ ] API gateway patterns (routing, aggregation)

## Contributing

Follow the existing code patterns:
- Use reactive programming (Mono/Flux)
- Minimize allocations in hot paths
- Add comprehensive tests
- Document performance characteristics
- Follow the existing project structure

## License

Part of The Last War trading system.

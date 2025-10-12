# Platform Schemas

This directory contains schema definitions for core domain models in The Last War trading system. Schemas are provided in multiple formats (JSON Schema, Avro, Protobuf) to support different use cases and integration patterns.

## Directory Structure

```
platform/schemas/
├── json/          # JSON Schema definitions
│   ├── Order.schema.json
│   ├── OrderEvent.schema.json
│   └── Allocation.schema.json (embedded in Order)
├── avro/          # Apache Avro schemas
│   ├── Order.avsc
│   └── OrderEvent.avsc
└── protobuf/      # Protocol Buffers definitions
    ├── Order.proto
    └── OrderEvent.proto
```

## Schema Formats

### JSON Schema
**Location:** `json/`  
**Extension:** `.schema.json`  
**Version:** Draft-07

**Use Cases:**
- REST API request/response validation
- OpenAPI/Swagger documentation
- Client-side validation in web applications
- Configuration file validation

**Validation Tools:**
- [AJV](https://ajv.js.org/) (JavaScript)
- [jsonschema](https://python-jsonschema.readthedocs.io/) (Python)
- [JSON Schema Validator](https://github.com/networknt/json-schema-validator) (Java)

**Example Usage:**
```javascript
const Ajv = require('ajv');
const ajv = new Ajv();
const orderSchema = require('./json/Order.schema.json');

const validate = ajv.compile(orderSchema);
const valid = validate(orderData);
if (!valid) console.log(validate.errors);
```

### Apache Avro
**Location:** `avro/`  
**Extension:** `.avsc`  
**Version:** 1.11+

**Use Cases:**
- Kafka topic serialization
- Schema Registry integration
- Efficient binary serialization
- Schema evolution with backward/forward compatibility

**Tools:**
- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
- [Apache Avro Tools](https://avro.apache.org/docs/current/)

**Example Usage:**
```java
// Register schema with Schema Registry
SchemaRegistryClient client = new CachedSchemaRegistryClient("http://localhost:8081", 100);
String subject = "order-value";
Schema schema = new Schema.Parser().parse(new File("avro/Order.avsc"));
int schemaId = client.register(subject, schema);

// Serialize order
KafkaAvroSerializer serializer = new KafkaAvroSerializer(client);
byte[] bytes = serializer.serialize("orders-topic", orderRecord);
```

### Protocol Buffers
**Location:** `protobuf/`  
**Extension:** `.proto`  
**Version:** proto3

**Use Cases:**
- gRPC service definitions
- High-performance binary serialization
- Cross-language data exchange
- Efficient network protocols

**Code Generation:**
```bash
# Java
protoc --java_out=./generated protobuf/Order.proto

# Python
protoc --python_out=./generated protobuf/Order.proto

# Go
protoc --go_out=./generated protobuf/Order.proto
```

**Example Usage:**
```java
// Serialize
Order order = Order.newBuilder()
    .setInternalOrderId(12345L)
    .setClientOrderId("ORDER-001")
    .setSymbol("AAPL")
    .setSide(Order.Side.BUY)
    .build();

byte[] bytes = order.toByteArray();

// Deserialize
Order order = Order.parseFrom(bytes);
```

## Core Schemas

### Order Schema
**Files:** `Order.schema.json`, `Order.avsc`, `Order.proto`

Complete order representation with state information for the OMS.

**Key Fields:**
- `internalOrderId` - System-assigned unique identifier (long)
- `clientOrderId` - Client-assigned identifier (string, max 64 chars)
- `symbol` - Trading symbol/instrument (string)
- `side` - Order side (BUY, SELL)
- `orderType` - Order type (MARKET, LIMIT, STOP, STOP_LIMIT)
- `quantity` - Total order quantity (long)
- `filledQuantity` - Quantity already filled (long)
- `remainingQuantity` - Quantity remaining (long)
- `price` - Order price in minimum increments (long)
- `state` - Current order state (NEW, ACCEPTED, WORKING, PARTIAL_FILL, FILLED, CANCELLED, REJECTED, EXPIRED)
- `timestamp` - Order creation timestamp in nanoseconds (long)
- `account` - Trading account identifier (long)
- `venue` - Trading venue/exchange identifier (string)
- `timeInForce` - Time in force (GTC, IOC, FOK, DAY)
- `allocations` - Array of fill allocations

### OrderEvent Schema
**Files:** `OrderEvent.schema.json`, `OrderEvent.avsc`, `OrderEvent.proto`

Event representing a state change in an order's lifecycle.

**Key Fields:**
- `eventId` - Unique event identifier (long)
- `internalOrderId` - Internal order identifier (long)
- `clientOrderId` - Client order identifier (string)
- `eventType` - Type of order event (ORDER_RECEIVED, ORDER_ACCEPTED, ORDER_REJECTED, etc.)
- `previousState` - State before this event
- `newState` - State after this event
- `timestamp` - Event timestamp in nanoseconds (long)
- `reason` - Optional reason for state change (string)
- `fillQuantity` - Quantity filled (for fill events, long)
- `fillPrice` - Fill price (for fill events, long)
- `executionId` - Execution ID (for fill events, long)

### Allocation Schema
**Note:** Embedded in Order schema

Represents a fill execution allocated to an order.

**Key Fields:**
- `allocationId` - Unique allocation identifier (long)
- `executionId` - Execution ID from matching engine (long)
- `fillPrice` - Execution price (long)
- `fillQuantity` - Filled quantity (long)
- `fillTimestamp` - Fill timestamp in nanoseconds (long)
- `venue` - Execution venue (string)
- `counterpartyOrderId` - Counterparty order ID for audit (long)

## Schema Validation

### Automated Validation
Schemas are automatically validated during the build process:

```bash
# Validate all schemas
./gradlew validateSchemas

# Validate JSON schemas only
./gradlew validateJsonSchemas

# Validate Avro schemas only
./gradlew validateAvroSchemas
```

### Manual Validation

#### JSON Schema
```bash
# Install jsonschema CLI
npm install -g ajv-cli

# Validate schema
ajv compile -s json/Order.schema.json

# Validate data against schema
ajv validate -s json/Order.schema.json -d sample-order.json
```

#### Avro Schema
```bash
# Install Avro tools
# Download from https://avro.apache.org/releases.html

# Validate schema
java -jar avro-tools.jar compile schema avro/Order.avsc /tmp/output

# Convert schema to JSON
java -jar avro-tools.jar tojson avro/Order.avsc
```

#### Protobuf Schema
```bash
# Validate by attempting to compile
protoc --java_out=/tmp protobuf/Order.proto
```

## Schema Evolution

### Versioning Strategy
- **Semantic versioning** for breaking changes
- **Schema Registry** for Avro schema versioning
- **Backward compatibility** maintained where possible

### Compatibility Rules

#### JSON Schema
- New optional fields: ✅ Compatible
- New required fields: ❌ Breaking change
- Removing fields: ❌ Breaking change
- Changing field types: ❌ Breaking change

#### Avro
- Adding fields with defaults: ✅ Backward compatible
- Removing fields with defaults: ✅ Forward compatible
- Changing field types: ❌ Breaking change (use union types)

#### Protobuf
- Adding new fields: ✅ Compatible (fields are optional by default in proto3)
- Removing fields: ⚠️ Safe if field numbers not reused
- Changing field types: ❌ Breaking change

### Migration Process
1. Create new schema version
2. Deploy consumers that can handle both old and new versions
3. Deploy producers with new schema
4. Deprecate old schema version
5. Remove old version after grace period (30 days)

## Best Practices

### 1. Field Types
- Use `long` (int64) for IDs and quantities (supports large values)
- Use `string` for symbols and identifiers
- Use enums for finite sets of values
- Use timestamps in nanoseconds (long) for precision

### 2. Naming Conventions
- camelCase for JSON Schema fields
- snake_case for Protobuf fields (convention)
- camelCase for Avro fields (Java convention)

### 3. Documentation
- All fields must have descriptions
- Include examples in schema documentation
- Document units (e.g., "price in cents", "timestamp in nanoseconds")

### 4. Validation
- Add min/max constraints for numeric fields
- Add length constraints for strings
- Use patterns for formatted strings (e.g., client order IDs)

### 5. Defaults
- Provide sensible defaults for optional fields
- Document default behavior

## Integration Examples

### REST API with JSON Schema
```java
@RestController
public class OrderController {
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@Valid @RequestBody Order order) {
        // JSON Schema validation via @Valid
        // ...
    }
}
```

### Kafka with Avro
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
props.put("schema.registry.url", "http://localhost:8081");

KafkaProducer<String, Order> producer = new KafkaProducer<>(props);
ProducerRecord<String, Order> record = new ProducerRecord<>("orders", orderId, order);
producer.send(record);
```

### gRPC with Protobuf
```java
// Generated from Order.proto
OrderServiceGrpc.OrderServiceBlockingStub stub = OrderServiceGrpc.newBlockingStub(channel);
Order order = Order.newBuilder()
    .setInternalOrderId(12345L)
    .setClientOrderId("ORDER-001")
    .build();
OrderResponse response = stub.createOrder(order);
```

## Tools and Resources

### Schema Tools
- [JSON Schema Validator](https://www.jsonschemavalidator.net/)
- [Avro Schema Editor](https://avro.apache.org/docs/current/)
- [Protobuf Online Compiler](https://protogen.marcgravell.com/)

### Documentation
- [JSON Schema Specification](https://json-schema.org/)
- [Apache Avro Documentation](https://avro.apache.org/docs/current/)
- [Protocol Buffers Guide](https://developers.google.com/protocol-buffers)

### Code Generation
- [quicktype](https://quicktype.io/) - Generate code from JSON Schema
- [Avro Maven Plugin](https://avro.apache.org/docs/current/gettingstartedjava.html)
- [protoc](https://github.com/protocolbuffers/protobuf) - Protocol Buffers compiler

## Contributing

When adding new schemas:
1. Create schema in all three formats (JSON, Avro, Protobuf)
2. Add validation examples
3. Update this README with new schema documentation
4. Add unit tests for schema validation
5. Update integration examples

---

**Location:** `/platform/schemas/`  
**Maintained by:** The Last War Architecture Team  
**Last Updated:** 2024-10-12

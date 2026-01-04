# Product Creation API with Spring Boot (Event-Driven)

This document outlines the implementation of a product creation API using Spring Boot, following an event-driven architecture, and integrating with existing workflows.

## 1. Introduction
This document details the process and architecture for implementing a product creation API within a Spring Boot application. The design emphasizes an event-driven approach, ensuring loose coupling, scalability, and seamless integration with other services and workflows in the Vernont ecosystem. The primary goal is to provide a robust, asynchronous mechanism for adding new products to the system.

## 2. Architectural Overview
The product creation process will leverage an event-driven architecture. When a product creation request is received, the API service will:
1. Validate the incoming request.
2. Persist the product data to the database.
3. Publish a `ProductCreatedEvent` to a message broker (e.g., Kafka, RabbitMQ).
4. Return an asynchronous response (e.g., 202 Accepted) to the client.

Downstream services (e.g., Inventory Service, Search Indexing Service, Notification Service) will subscribe to `ProductCreatedEvent` and react accordingly, ensuring data consistency and enabling parallel processing without tight coupling.

```mermaid
graph TD
    A[Client] -->|POST /api/products| B(Product Creation API - Spring Boot)
    B --> C{Validate Request}
    C --> D[Product Service]
    D --> E[Product Repository (Database)]
    E --> F[Event Publisher]
    F --> G(Message Broker)
    G --> H[Inventory Service]
    G --> I[Search Indexing Service]
    G --> J[Other Workflow Services]
    D --> K{Return 202 Accepted}
```

## 3. API Endpoint Definition

### `POST /api/products`

Creates a new product in the system.

#### 3.1. Request Payload

The request body should be a JSON object representing the product to be created.

```json
{
  "name": "Example Product",
  "description": "A detailed description of the example product.",
  "sku": "EXP-001",
  "price": 29.99,
  "currency": "USD",
  "quantity": 100,
  "categoryIds": ["categoryId1", "categoryId2"],
  "imageUrl": "http://example.com/images/example-product.jpg",
  "attributes": {
    "color": "red",
    "size": "M"
  }
}
```

**Fields:**

*   `name` (String, required): The name of the product.
*   `description` (String, optional): A brief description of the product.
*   `sku` (String, required, unique): Stock Keeping Unit for the product.
*   `price` (Number, required): The price of the product.
*   `currency` (String, required): The currency code (e.g., "USD", "EUR").
*   `quantity` (Number, required): Initial stock quantity.
*   `categoryIds` (Array<String>, optional): List of category IDs the product belongs to.
*   `imageUrl` (String, optional): URL of the product's main image.
*   `attributes` (Object, optional): Key-value pairs for product-specific attributes (e.g., color, size, material).

#### 3.2. Response Payload

Upon successful receipt and initial processing (product persisted, event published), the API will return a `202 Accepted` status code. The response body will contain the `productId` and a confirmation message.

```json
{
  "productId": "generatedProductId123",
  "message": "Product creation request accepted. Processing asynchronously."
}
```

**Fields:**

*   `productId` (String): The unique identifier for the newly created product.
*   `message` (String): A confirmation message.## 4. Spring Boot Implementation Details

### 4.1. Controller (`ProductController.kt`)
The `ProductController` will expose the REST endpoint for creating products. It will handle incoming HTTP requests, perform basic input validation, and delegate the business logic to the `ProductService`.

```kotlin
package com.vernont.api.product

import com.vernont.application.product.ProductService
import com.vernont.application.product.ProductCreationRequest
import com.vernont.application.product.ProductCreationResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService
) {

    @PostMapping
    fun createProduct(@Valid @RequestBody request: ProductCreationRequest): ResponseEntity<ProductCreationResponse> {
        // Basic validation can be done here or in a dedicated validator
        // For more complex validation, consider using a separate validation layer

        val productId = productService.createProduct(request)
        return ResponseEntity.accepted()
            .body(ProductCreationResponse(productId, "Product creation request accepted. Processing asynchronously."))
    }
}
```

### 4.2. Service (`ProductService.kt`)
The `ProductService` contains the core business logic for product creation. It orchestrates the persistence of the product entity and the publishing of the `ProductCreated` event.

```kotlin
package com.vernont.application.product

import com.vernont.domain.product.Product
import com.vernont.infrastructure.product.ProductRepository
import com.vernont.events.EventPublisher
import com.vernont.events.ProductCreated
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal // Import BigDecimal
import java.util.UUID

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher
) {

    @Transactional
    fun createProduct(request: ProductCreationRequest): String {
        val product = convertRequestToProductEntity(request)
        val savedProduct = productRepository.save(product)
        
        // Publish event after successful persistence, using the ProductCreated domain event
        eventPublisher.publish(
            ProductCreated(
                aggregateId = savedProduct.id,
                name = savedProduct.name,
                description = savedProduct.description ?: "", // Provide a default for nullable description
                price = savedProduct.price,
                sku = savedProduct.sku,
                quantity = savedProduct.quantity,
                categoryId = savedProduct.categoryIds?.firstOrNull() ?: "default_category_id" // Use first category or a default
            )
        )
        
        return savedProduct.id
    }

    private fun convertRequestToProductEntity(request: ProductCreationRequest): Product {
        // Map request DTO to Product entity
        return Product(
            id = UUID.randomUUID().toString(),
            name = request.name,
            description = request.description,
            sku = request.sku,
            price = request.price,
            currency = request.currency,
            quantity = request.quantity,
            categoryIds = request.categoryIds,
            imageUrl = request.imageUrl,
            attributes = request.attributes
        )
    }
}
```

### 4.3. Repository (`ProductRepository.kt`)
The `ProductRepository` is responsible for data access operations related to the `Product` entity. In a Spring Boot application, this is typically an interface extending `JpaRepository` or a similar Spring Data interface.

```kotlin
package com.vernont.infrastructure.product

import com.vernont.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, String> {
    // Custom query methods can be defined here if needed
    fun findBySku(sku: String): Optional<Product>
}
```
*(Note: A `Product` JPA Entity (a Kotlin data class annotated with `@Entity`) would need to be defined to map to the database table.)*

### 4.4. Event Publisher (`EventPublisher.kt`)
The `EventPublisher` is an abstraction for publishing domain events. This allows the `ProductService` to remain decoupled from the specific messaging technology (e.g., Kafka, RabbitMQ).

```kotlin
package com.vernont.events

interface EventPublisher {
    fun publish(event: Any)
}
```

An example implementation using Spring's `ApplicationEventPublisher` (for in-process events) or a dedicated Kafka/RabbitMQ sender:

```kotlin
package com.vernont.infrastructure.events

import com.vernont.events.EventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : EventPublisher {

    override fun publish(event: Any) {
        // For demonstration, we use Spring's ApplicationEventPublisher for in-process events.
        // In a real-world event-driven architecture, this would typically involve
        // sending messages to a message broker like Kafka or RabbitMQ.
        println("Publishing event: ${event::class.simpleName} - $event")
        applicationEventPublisher.publishEvent(event)
    }
}
```
*(Note: For external message brokers, specific client libraries (e.g., Spring Kafka, Spring AMQP) would be used here.)*## 5. Event-Driven Architecture
The product creation API is built upon an event-driven architecture. This pattern promotes loose coupling between services, improves scalability, and enables real-time reactions to business events. When a product is successfully created, a `ProductCreatedEvent` is published, which can then be consumed by various downstream services.

### 5.1. `ProductCreated` Structure (from `com.vernont.events`)
Instead of defining a new event, we will use the existing `ProductCreated` domain event available in `com.vernont.events`. This event captures the essential information about a newly created product.

```kotlin
package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Fired when a new product is created in the system.
 *
 * @property name Product name
 * @property description Product description
 * @property price Product price
 * @property sku Stock keeping unit
 * @property quantity Initial quantity in stock
 * @property categoryId ID of the product category
 */
data class ProductCreated(
    override val aggregateId: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val sku: String,
    val quantity: Int,
    val categoryId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
```

### 5.2. Event Publishing Mechanism
As demonstrated in the `ProductService`, the `ProductCreated` event is published immediately after the product data is successfully persisted to the database. The `EventPublisher` abstraction ensures that the service remains unaware of the underlying messaging technology.

In a production environment, `EventPublisher` would typically integrate with a robust message broker like Apache Kafka or RabbitMQ.

**Using Kafka (Conceptual Example):**
```kotlin
package com.vernont.infrastructure.events

import com.vernont.events.EventPublisher
import com.vernont.events.ProductCreated
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.stereotype.Component
import java.util.HashMap

@Configuration
class KafkaProducerConfig {
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps: MutableMap<String, Any> = HashMap()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }
}

@Component
@Primary // Mark as primary if multiple EventPublisher beans exist
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : EventPublisher {

    companion object {
        private const val PRODUCT_TOPIC = "product_events"
    }

    override fun publish(event: Any) {
        if (event is ProductCreated) { // Changed to ProductCreated
            kafkaTemplate.send(PRODUCT_TOPIC, event.aggregateId, event) // Use aggregateId as key
            println("Published ProductCreated event to Kafka: $event")
        }
        // Handle other event types or throw an exception for unsupported events
    }
}
```

This setup ensures that:
*   **Asynchronous Processing:** Downstream services can process events without blocking the product creation API.
*   **Decoupling:** Services do not need to know about each other's existence, only about the events they produce or consume.
*   **Durability:** Message brokers provide persistence for events, ensuring they are not lost even if consumers are temporarily unavailable.## 6. Workflow Integration
The `ProductCreatedEvent` serves as a trigger for various downstream services and workflows, enabling a decoupled and scalable system. Upon receiving this event from the message broker, subscribing services can initiate their respective processes.

Here are examples of how existing workflows can integrate with the `ProductCreatedEvent`:

### 6.1. Inventory Management Workflow
*   **Listener:** An `InventoryService` (or a dedicated event listener within it) subscribes to `PRODUCT_TOPIC`.
*   **Action:** When a `ProductCreated` event is received, the `InventoryService` initializes the stock levels for the new product, potentially setting a default initial quantity or flagging it for manual review if the initial quantity from the event is zero.
*   **Example Listener (Spring Boot Kafka Consumer):**
    ```kotlin
    package com.vernont.application.inventory

    import com.vernont.events.ProductCreated
    import org.springframework.kafka.annotation.KafkaListener
    import org.springframework.stereotype.Component

    @Component
    class InventoryEventListener(
        private val inventoryService: InventoryService // Assuming an InventoryService exists
    ) {

        @KafkaListener(topics = ["product_events"], groupId = "inventory-group")
        fun listenProductEvents(event: ProductCreated) {
            println("Inventory Service received ProductCreated event: ${event.aggregateId}")
            inventoryService.initializeProductStock(event.aggregateId, event.quantity)
            // Further logic for inventory system integration
        }
    }
    ```

### 6.2. Search Indexing Workflow
*   **Listener:** A `SearchService` (or a search indexer microservice) subscribes to `PRODUCT_TOPIC`.
*   **Action:** Upon receiving a `ProductCreatedEvent`, the `SearchService` extracts relevant product details (e.g., `productId`, `name`, `description`, `categoryIds`, `attributes`) and indexes them in a search engine like Elasticsearch or Apache Solr, making the new product discoverable through search queries.

### 6.3. Marketing & Promotion Workflow
*   **Listener:** A `MarketingService` subscribes to `PRODUCT_TOPIC`.
*   **Action:** This service might trigger various marketing activities:
    *   Adding the new product to a "New Arrivals" section on the website.
    *   Scheduling social media posts.
    *   Sending internal notifications to the marketing team for campaign planning.

### 6.4. Notification Service Workflow
*   **Listener:** A `NotificationService` subscribes to `PRODUCT_TOPIC`.
*   **Action:** This service could send notifications to relevant stakeholders:
    *   An email to product managers confirming the new product.
    *   A message to a Slack channel for product team awareness.

### 6.5. Data Analytics Workflow
*   **Listener:** An `AnalyticsService` subscribes to `PRODUCT_TOPIC`.
*   **Action:** The service can ingest the new product data into a data warehouse for analytical purposes, tracking product trends, sales performance, and other business intelligence metrics from the moment the product is created.

By leveraging these event-driven integrations, the product creation process becomes a central point that seamlessly updates multiple interconnected systems without requiring direct calls or tight dependencies between them. This approach enhances system resilience, scalability, and maintainability.
## 7. Error Handling
Robust error handling is crucial for any production-ready API. In the product creation API, several types of errors can occur:

*   **Validation Errors:** Invalid input data (e.g., missing required fields, incorrect formats).
*   **Business Logic Errors:** Conflicts (e.g., duplicate SKU), or issues during persistence.
*   **System Errors:** Database connection issues, message broker unavailability.

Spring Boot provides mechanisms like `@ControllerAdvice` and `@ExceptionHandler` to centralize error handling.

**Example `GlobalExceptionHandler.kt`:**
```kotlin
package com.vernont.api.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.HashMap

data class ErrorResponse(
    val message: String,
    val details: Any? = null
)

class DuplicateProductSkuException(message: String) : RuntimeException(message)

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = HashMap<String, String>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage
            errors[fieldName] = errorMessage ?: "Validation error"
        }
        return ResponseEntity(ErrorResponse("Validation Failed", errors), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DuplicateProductSkuException::class)
    fun handleDuplicateSkuException(ex: DuplicateProductSkuException): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse("Business Logic Error", ex.message), HttpStatus.CONFLICT)
    }

    @ExceptionHandler(Exception::class)
    fun handleGlobalException(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse("Internal Server Error", ex.message), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
```

## 8. Validation
Input validation ensures data integrity and prevents malformed requests from entering the system. Spring Boot supports JSR 380 (Bean Validation) annotations.

**Example `ProductCreationRequest.kt` with validation:**
```kotlin
package com.vernont.application.product

import jakarta.validation.constraints.*
import java.util.List
import java.util.Map

data class ProductCreationRequest(
    @field:NotBlank(message = "Product name is required")
    val name: String,

    val description: String?,

    @field:NotBlank(message = "SKU is required")
    @field:Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "SKU must be 3-20 alphanumeric characters or hyphens")
    val sku: String,

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.01", message = "Price must be greater than 0")
    val price: Double,

    @field:NotBlank(message = "Currency is required")
    val currency: String,

    @field:Min(value = 0, message = "Quantity cannot be negative")
    val quantity: Int,

    val categoryIds: List<String>?,
    val imageUrl: String?,
    val attributes: Map<String, String>?
)
```
The `@Valid` annotation in the controller enables this validation:
```kotlin
fun createProduct(@Valid @RequestBody request: ProductCreationRequest): ResponseEntity<ProductCreationResponse> {
    // ...
}
```

## 9. Testing
Comprehensive testing is essential to ensure the reliability and correctness of the API.

*   **Unit Tests:** Test individual components (Controller, Service, Repository, Event Publisher implementations) in isolation using mocking frameworks (e.g., Mockito).
*   **Integration Tests:** Test the interaction between components, for example, verifying that the `ProductService` correctly persists a product and publishes an event. Spring Boot's `@SpringBootTest` and `@DataJpaTest` annotations are useful here.
*   **Contract Tests:** If using an external message broker, contract testing (e.g., Spring Cloud Contract) can ensure that the `ProductCreated` contract is maintained between producer and consumers.
*   **End-to-End Tests:** Test the entire flow from API request to downstream workflow triggers using tools like Postman, Newman, or Cypress.

**Example Service Unit Test:**
```kotlin
package com.vernont.application.product

import com.vernont.domain.product.Product
import com.vernont.infrastructure.product.ProductRepository
import com.vernont.events.EventPublisher
import com.vernont.events.ProductCreated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.UUID

// Assuming a Product domain entity exists, e.g., in com.vernont.domain.product
// data class Product(
//     val id: String,
//     val name: String,
//     val description: String?,
//     val sku: String,
//     val price: BigDecimal,
//     val currency: String,
//     val quantity: Int,
//     val categoryIds: List<String>?,
//     val imageUrl: String?,
//     val attributes: Map<String, String>?
// )

@ExtendWith(MockitoExtension::class)
class ProductServiceTest {

    @Mock
    private lateinit var productRepository: ProductRepository
    @Mock
    private lateinit var eventPublisher: EventPublisher

    @InjectMocks
    private lateinit var productService: ProductService

    @Test
    fun `createProduct should save product and publish event`() {
        val request = ProductCreationRequest(
            name = "Test Product",
            sku = "TEST-001",
            price = 10.00, // Double for request DTO
            currency = "USD",
            quantity = 50,
            description = null,
            categoryIds = null,
            imageUrl = null,
            attributes = null
        )

        val savedProduct = Product(
            id = UUID.randomUUID().toString(),
            name = "Test Product",
            description = "Some description",
            sku = "TEST-001",
            price = BigDecimal("10.00"), // BigDecimal for domain entity
            currency = "USD",
            quantity = 50,
            categoryIds = listOf("default_category_id"),
            imageUrl = null,
            attributes = null
        )

        `when`(productRepository.save(any(Product::class.java))).thenReturn(savedProduct)

        val productId = productService.createProduct(request)

        assertEquals(savedProduct.id, productId)
        verify(productRepository, times(1)).save(any(Product::class.java))
        verify(eventPublisher, times(1)).publish(
            check<ProductCreated> { event ->
                assertEquals(savedProduct.id, event.aggregateId)
                assertEquals(savedProduct.name, event.name)
                assertEquals(savedProduct.sku, event.sku)
                assertEquals(savedProduct.price, event.price) // Now comparing BigDecimal
                assertEquals(savedProduct.quantity, event.quantity)
                assertEquals(savedProduct.categoryIds?.first(), event.categoryId)
            }
        )
    }
}
```

**Example Controller Integration Test:**
```kotlin
package com.vernont.api.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.application.product.ProductCreationRequest
import com.vernont.application.product.ProductService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var productService: ProductService // Mock the service layer

    private val objectMapper = ObjectMapper()

    @Test
    fun `createProduct should return accepted and productId`() {
        val request = ProductCreationRequest(
            name = "Integration Test Product",
            sku = "INT-001",
            price = 25.00,
            currency = "EUR",
            quantity = 75,
            description = null,
            categoryIds = null,
            imageUrl = null,
            attributes = null
        )

        val expectedProductId = UUID.randomUUID().toString()
        `when`(productService.createProduct(any(ProductCreationRequest::class.java))).thenReturn(expectedProductId)

        mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.productId").value(expectedProductId))
            .andExpect(jsonPath("$.message").value("Product creation request accepted. Processing asynchronously."))

        verify(productService, times(1)).createProduct(any(ProductCreationRequest::class.java))
    }
}
```## 10. Conclusion

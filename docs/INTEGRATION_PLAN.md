# NexusCommerce Integration Architecture Plan

## 🏗️ Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     API Layer (Controllers)                  │
│  - REST endpoints (Admin API, Store API)                    │
│  - Request validation, authentication                        │
│  - DTO mapping                                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Application Layer (Services)               │
│  - Business logic orchestration                              │
│  - Transaction management (@Transactional)                   │
│  - Calls workflows for complex operations                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Workflow Layer (Workflows)                │
│  - Multi-step business processes                            │
│  - Compensation/rollback logic                              │
│  - Calls multiple services/repositories                      │
│  - Publishes domain events                                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  Repository Layer (Data Access)              │
│  - JPA repositories with Named Entity Graphs                │
│  - Query methods with @EntityGraph annotations              │
│  - Custom queries for complex operations                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Entities)                   │
│  - 40+ JPA entities                                          │
│  - Business logic methods                                    │
│  - Domain invariants                                         │
└─────────────────────────────────────────────────────────────┘

                       ⚡ Events Flow ⚡
┌─────────────────────────────────────────────────────────────┐
│              Event-Driven Cross-Cutting Concerns            │
│                                                              │
│  Workflows/Services → Publish Events → Event Listeners       │
│                                                              │
│  Examples:                                                   │
│  - ProductCreated → Update search index                     │
│  - OrderCompleted → Send email, Update inventory            │
│  - CustomerRegistered → Send welcome email                  │
└─────────────────────────────────────────────────────────────┘
```

## 📋 Integration Flow Examples

### Example 1: Create Product (Simple Operation)

```
1. API Controller (ProductController.createProduct)
   ↓
2. Service (ProductService.createProduct)
   - Validates input
   - Creates product entity
   - Saves via repository
   - Publishes ProductCreated event
   ↓
3. Repository (ProductRepository.save)
   - Persists to database
   ↓
4. Event Listeners
   - EmailListener → Send notification
   - SearchIndexListener → Index product
```

### Example 2: Create Order (Complex Workflow)

```
1. API Controller (OrderController.createOrder)
   ↓
2. Service (OrderService.createOrder)
   - Validates cart
   - Executes CreateOrderWorkflow
   ↓
3. Workflow (CreateOrderWorkflow)
   Step 1: Validate Cart
   Step 2: Reserve Inventory (with compensation)
   Step 3: Process Payment (with compensation)
   Step 4: Create Order
   Step 5: Create Fulfillment
   Step 6: Publish OrderCreated event
   ↓
4. Multiple Repositories
   - CartRepository
   - InventoryRepository
   - PaymentRepository
   - OrderRepository
   ↓
5. Event Listeners
   - EmailListener → Send order confirmation
   - InventoryListener → Update stock levels
   - AnalyticsListener → Track conversion
```

## 🔧 Module Structure

### vernont-application (Service Layer)

```
src/main/kotlin/com/vernont/application/
├── product/
│   ├── ProductService.kt
│   ├── ProductVariantService.kt
│   └── dto/
│       ├── CreateProductRequest.kt
│       └── ProductResponse.kt
├── order/
│   ├── OrderService.kt
│   └── dto/
├── customer/
│   ├── CustomerService.kt
│   └── dto/
├── cart/
│   ├── CartService.kt
│   └── dto/
└── common/
    └── BaseService.kt
```

### vernont-domain (Repository Layer)

```
src/main/kotlin/com/vernont/domain/
├── product/
│   ├── Product.kt (entity)
│   ├── ProductRepository.kt
│   └── ProductVariant.kt (entity)
│       └── ProductVariantRepository.kt
└── ...other domains
```

### vernont-workflow (Workflow Layer)

```
src/main/kotlin/com/vernont/workflow/
├── engine/
│   └── WorkflowEngine.kt (already created)
├── steps/
│   └── WorkflowStep.kt (already created)
└── flows/
    ├── product/
    │   └── CreateProductWorkflow.kt
    ├── order/
    │   ├── CreateOrderWorkflow.kt
    │   ├── CompleteOrderWorkflow.kt
    │   └── CancelOrderWorkflow.kt
    └── cart/
        └── CheckoutWorkflow.kt
```

### vernont-events (Event Layer)

```
src/main/kotlin/com/vernont/events/
├── publisher/
│   └── EventPublisher.kt (already created)
├── domain/
│   ├── ProductEvents.kt (already created)
│   ├── OrderEvents.kt (already created)
│   └── ...
└── listener/
    ├── EmailEventListener.kt
    ├── InventoryEventListener.kt
    └── SearchIndexEventListener.kt
```

### vernont-api (API Layer)

```
src/main/kotlin/com/vernont/api/
├── admin/
│   ├── ProductController.kt
│   ├── OrderController.kt
│   └── CustomerController.kt
├── store/
│   ├── ProductController.kt
│   ├── CartController.kt
│   └── CheckoutController.kt
├── config/
│   ├── SecurityConfig.kt
│   └── CorsConfig.kt
└── security/
    └── JwtAuthenticationFilter.kt
```

## 🔄 Integration Patterns

### Pattern 1: Simple CRUD (Repository → Service → API)

Used for: Products, Customers, basic operations

```kotlin
// Repository
@Repository
interface ProductRepository : JpaRepository<Product, String> {
    @EntityGraph("Product.full")
    fun findByIdWithFull(id: String): Product?
}

// Service
@Service
@Transactional
class ProductService(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher
) {
    fun createProduct(request: CreateProductRequest): Product {
        val product = Product().apply {
            title = request.title
            handle = request.handle
            // ... map fields
        }

        val saved = productRepository.save(product)
        eventPublisher.publish(ProductCreated(saved.id, saved.title))

        return saved
    }
}

// Controller
@RestController
@RequestMapping("/admin/products")
class ProductController(private val productService: ProductService) {

    @PostMapping
    fun createProduct(@RequestBody request: CreateProductRequest): ProductResponse {
        val product = productService.createProduct(request)
        return ProductResponse.from(product)
    }
}
```

### Pattern 2: Complex Workflow (Repository → Workflow → Service → API)

Used for: Order creation, checkout, payment processing

```kotlin
// Workflow
@Component
class CreateOrderWorkflow(
    private val cartRepository: CartRepository,
    private val inventoryService: InventoryService,
    private val paymentService: PaymentService,
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher
) : Workflow<CreateOrderInput, Order> {

    override val name = "create-order"

    override suspend fun execute(input: CreateOrderInput, context: WorkflowContext): WorkflowResult<Order> {
        // Step 1: Validate and get cart
        val cart = cartRepository.findById(input.cartId).orElseThrow()

        // Step 2: Reserve inventory (with compensation)
        val reservation = inventoryService.reserveForCart(cart)
        context.addMetadata("reservationId", reservation.id)

        try {
            // Step 3: Process payment
            val payment = paymentService.authorizePayment(input.paymentDetails, cart.total)

            // Step 4: Create order
            val order = Order().apply {
                // ... map from cart
            }
            val savedOrder = orderRepository.save(order)

            // Step 5: Publish event
            eventPublisher.publish(OrderCreated(savedOrder.id, savedOrder.customerId))

            return WorkflowResult.success(savedOrder)

        } catch (e: Exception) {
            // Compensation: Release inventory
            inventoryService.releaseReservation(reservation.id)
            throw e
        }
    }
}

// Service
@Service
class OrderService(
    private val workflowEngine: WorkflowEngine,
    private val createOrderWorkflow: CreateOrderWorkflow
) {
    suspend fun createOrder(input: CreateOrderInput): Order {
        val result = workflowEngine.executeWorkflow(createOrderWorkflow, input)
        return result.getOrThrow()
    }
}

// Controller
@RestController
@RequestMapping("/store/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping
    suspend fun createOrder(@RequestBody request: CreateOrderRequest): OrderResponse {
        val order = orderService.createOrder(request.toInput())
        return OrderResponse.from(order)
    }
}
```

### Pattern 3: Event-Driven Side Effects

```kotlin
// Event Listener
@Component
class OrderEventListener(
    private val emailService: EmailService,
    private val inventoryService: InventoryService
) {

    @EventListener
    @Async
    fun handleOrderCreated(event: OrderCreated) {
        // Send confirmation email
        emailService.sendOrderConfirmation(event.orderId)

        // Update analytics
        // ...
    }

    @EventListener
    @Async
    fun handleOrderCompleted(event: OrderCompleted) {
        // Send shipping notification
        emailService.sendShippingNotification(event.orderId)

        // Update inventory
        inventoryService.fulfillOrder(event.orderId)
    }
}
```

## 🎯 Implementation Order

1. **Phase 1: Repository Layer** ✅ (Entities already created)
   - Create all JPA repositories
   - Add Named Entity Graph queries
   - Add custom query methods

2. **Phase 2: Service Layer**
   - Create basic services for each domain
   - Implement CRUD operations
   - Add transaction management

3. **Phase 3: Workflow Layer**
   - Implement key workflows:
     - CreateOrderWorkflow
     - CheckoutWorkflow
     - CompleteOrderWorkflow
     - ProcessPaymentWorkflow

4. **Phase 4: Event Integration**
   - Create event listeners
   - Wire events to services/workflows
   - Implement async event handling

5. **Phase 5: API Layer**
   - Create REST controllers
   - Add security
   - Add validation
   - Add API documentation

6. **Phase 6: Testing & Integration**
   - Integration tests
   - End-to-end workflows
   - Performance testing

## 📊 Key Integration Points

### 1. Transaction Boundaries

```kotlin
@Service
@Transactional  // Transaction starts here
class OrderService {
    fun createOrder() {
        // All repository operations in single transaction
        // Events published after transaction commits
    }
}
```

### 2. Event Publishing Strategy

```kotlin
// Publish events AFTER transaction commits
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: DomainEvent) {
    // Process after DB commit
}
```

### 3. Caching Strategy

```kotlin
@Service
class ProductService {

    @Cacheable("products")
    fun findById(id: String): Product? {
        return productRepository.findByIdWithFull(id)
    }

    @CacheEvict("products", key = "#product.id")
    fun update(product: Product): Product {
        return productRepository.save(product)
    }
}
```

### 4. Error Handling

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowException::class)
    fun handleWorkflowException(ex: WorkflowException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
    }
}
```

## 🚀 Next Steps

Now I'll implement:
1. All repositories with Named Entity Graphs
2. Core services for Product, Order, Cart, Customer
3. Real-world workflows (CreateOrder, Checkout, etc.)
4. Event listeners for cross-cutting concerns
5. REST API controllers
6. Security configuration

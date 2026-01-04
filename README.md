# NexusCommerce 🚀

A modern, production-ready e-commerce backend built with **Kotlin** and **Spring Boot 4.x**, designed as a powerful replacement for Medusa.js with enterprise-grade architecture.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io)

## ✨ Features

- 🏗️ **Multi-Module Architecture** - Clean separation of concerns
- 🔄 **Workflow Engine** - Medusa-like orchestration with compensation support
- 📨 **Event-Driven** - Spring Application Events for reactive programming
- ⚡ **N+1 Prevention** - Named Entity Graphs for optimal query performance
- 🔐 **Redis Sessions** - Distributed session management and caching
- 📧 **SendGrid Integration** - Production-ready email service
- ☁️ **S3 Storage** - AWS S3 integration for file management
- 🔄 **Flyway Migrations** - Database version control
- 📊 **Monitoring** - Prometheus + Grafana for metrics
- 🔍 **OpenAPI/Swagger** - Auto-generated API documentation

## 🏗️ Architecture Overview

```
vernont-backend/
├── vernont-domain/          # 40+ JPA entities with Named Entity Graphs
│   ├── product/           # Products, variants, collections, categories
│   ├── customer/          # Customers, addresses, groups
│   ├── order/             # Orders, line items, addresses
│   ├── cart/              # Shopping carts and items
│   ├── region/            # Regions, countries, currencies, taxes
│   ├── store/             # Stores, sales channels, API keys
│   ├── inventory/         # Stock locations, inventory levels
│   ├── fulfillment/       # Shipping providers, options, fulfillments
│   ├── payment/           # Payment providers, payments, refunds
│   └── promotion/         # Promotions, rules, discounts
├── vernont-core/            # Core business logic and interfaces
├── vernont-application/     # Application services and use cases
├── vernont-workflow/        # Workflow engine with steps and compensation
├── vernont-events/          # Domain events (Product, Order, Customer, Cart)
├── vernont-infrastructure/  # External integrations (SendGrid, S3, Redis)
└── vernont-api/             # REST API, security, Flyway migrations
```

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Gradle 8.11+

### Run with Docker Compose

```bash
# Clone the repository
cd /Users/kibuka/IdeaProjects/vernont-backend

# Start all services (PostgreSQL, Redis, NexusCommerce, Prometheus, Grafana)
docker-compose up --build

# The API will be available at http://localhost:9000
# Swagger UI: http://localhost:9000/swagger-ui.html
# Actuator: http://localhost:9000/actuator
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3001 (admin/admin)
```

### Run Locally

```bash
# Set environment variables
export DATABASE_URL=jdbc:postgresql://localhost:5432/vernont
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Build and run
gradle :vernont-api:bootRun
```

## 📊 Domain Model

### Product Domain
- **Product** - Main product entity with variants, images, options
- **ProductVariant** - SKU-level variants with pricing
- **ProductCollection** - Product groupings
- **ProductCategory** - Hierarchical categories
- **ProductType** - Product type classification
- **ProductTag** - Tagging system

### Customer Domain
- **Customer** - Customer accounts
- **CustomerAddress** - Shipping/billing addresses
- **CustomerGroup** - Customer segmentation

### Order Domain
- **Order** - Order management with status tracking
- **OrderLineItem** - Order items with fulfillment tracking
- **OrderAddress** - Immutable address snapshots

### Cart Domain
- **Cart** - Shopping cart with totals
- **CartLineItem** - Cart items

### Region & Store
- **Region** - Geographic regions with tax config
- **Country** - Country ISO codes
- **Currency** - Multi-currency support
- **Store** - Multi-store management
- **SalesChannel** - Channel isolation

### Inventory
- **InventoryItem** - SKU inventory tracking
- **InventoryLevel** - Stock per location
- **StockLocation** - Warehouses/stores

### Fulfillment
- **FulfillmentProvider** - Shipping providers
- **ShippingOption** - Shipping methods
- **Fulfillment** - Shipment tracking

### Payment
- **PaymentProvider** - Payment gateways
- **Payment** - Payment transactions
- **Refund** - Refund processing

### Promotions
- **Promotion** - Discount campaigns
- **PromotionRule** - Conditional rules
- **Discount** - Applied discounts

## 🔄 Workflow Engine

Create Medusa-like workflows with compensation:

```kotlin
val createOrderWorkflow = object : Workflow<CreateOrderInput, Order> {
    override val name = "create-order"

    override suspend fun execute(input: CreateOrderInput, context: WorkflowContext): WorkflowResult<Order> {
        // Step 1: Validate cart
        val validateStep = createStep("validate-cart") { cart, ctx ->
            // Validation logic
            StepResponse.of(validatedCart)
        }

        // Step 2: Reserve inventory
        val reserveStep = createStep("reserve-inventory",
            execute = { cart, ctx ->
                inventoryService.reserve(cart.items)
                StepResponse.of(reservation, reservation.id)
            },
            compensate = { cart, ctx ->
                // Rollback inventory reservation
                inventoryService.release(ctx.getMetadata("reservationId"))
            }
        )

        // Execute workflow
        val cart = validateStep.invoke(input.cart, context)
        val reservation = reserveStep.invoke(cart.data, context)

        return WorkflowResult.success(order)
    }
}
```

## 📨 Event-Driven Architecture

```kotlin
// Publish events
eventPublisher.publish(ProductCreated(productId, name, price))

// Listen to events
@EventListener
fun handleProductCreated(event: ProductCreated) {
    // Send email, update search index, etc.
    emailService.sendProductNotification(event)
}
```

## ⚡ N+1 Query Prevention

All entities include Named Entity Graphs:

```kotlin
@Repository
interface ProductRepository : JpaRepository<Product, String> {

    @EntityGraph("Product.full")  // Loads variants, images, options, etc.
    fun findByIdWithFull(id: String): Product?

    @EntityGraph("Product.withVariants")  // Only loads variants with prices
    fun findAllWithVariants(): List<Product>
}
```

## 🔧 Configuration

### Environment Variables

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/vernont
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=your-secret-key

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8000,https://api.neoxus.co.uk,https://neoxus.co.uk,https://www.neoxus.co.uk,https://admin.neoxus.co.uk

# SendGrid
SENDGRID_API_KEY=your-sendgrid-key
SENDGRID_FROM_EMAIL=noreply@example.com

# AWS S3
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_S3_BUCKET=your-bucket
AWS_REGION=us-east-1
```

## 📚 API Documentation

Once running, visit:
- Swagger UI: `http://localhost:9000/swagger-ui.html`
- OpenAPI JSON: `http://localhost:9000/v3/api-docs`

## 🔐 Security

- JWT-based authentication
- Redis session management
- OAuth2 resource server support
- CORS configuration
- API key authentication for storefronts

## 📈 Monitoring

- Actuator endpoints: `/actuator/*`
- Prometheus metrics: `/actuator/prometheus`
- Grafana dashboards on port 3001

## 🧪 Testing

```bash
# Run all tests
gradle test

# Run specific module tests
gradle :vernont-domain:test
```

## 📝 Database Migrations

Flyway migrations are in `vernont-api/src/main/resources/db/migration/`:

- V1: Product tables
- V2: Customer tables
- V3: Cart and Order tables
- V4: Region and Store tables
- V5: Inventory and Fulfillment tables
- V6: Payment and Promotion tables

## 🛠️ Development

### Build Modules

```bash
# Build all modules
gradle build

# Build specific module
gradle :vernont-api:build
```

### Generate Build JAR

```bash
gradle :vernont-api:bootJar
```

## 🚢 Deployment

The application is containerized and ready for deployment to:
- Kubernetes
- AWS ECS/Fargate
- Google Cloud Run
- Azure Container Instances
- Any Docker-compatible platform

## 📄 License

MIT License

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

**Built with ❤️ using Kotlin and Spring Boot**

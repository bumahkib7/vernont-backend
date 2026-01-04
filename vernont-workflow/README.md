# Vernont Workflow Engine

## Overview

The Vernont Workflow Engine is a production-ready, distributed workflow orchestration system inspired by Medusa's workflow architecture. It provides robust business process automation with features like compensation, retry mechanisms, distributed locking, and comprehensive monitoring.

## 🚀 Production Features

### ✅ **Core Features**
- **Persistent Execution Tracking**: All workflow executions are stored in PostgreSQL
- **Distributed Locking**: Redis-based locking prevents concurrent execution conflicts
- **Type Safety**: Compile-time type checking for workflow inputs/outputs
- **Retry Mechanisms**: Automatic retry with configurable limits
- **Timeout Handling**: Configurable execution timeouts with automatic cleanup
- **Compensation/Rollback**: Saga pattern implementation for failure recovery
- **Metrics & Monitoring**: Micrometer integration with detailed metrics
- **Health Checks**: Spring Actuator integration for operational visibility

### ✅ **Enterprise Ready**
- **Multi-module Architecture**: Clean separation following DDD principles
- **Database Integration**: JPA entities following project patterns
- **Spring Boot Integration**: Full Spring ecosystem support
- **Production Configuration**: Environment-specific configurations
- **Comprehensive Testing**: Unit and integration test coverage
- **Scheduled Maintenance**: Automatic cleanup and monitoring tasks

## 🏗️ Architecture

```
vernont-workflow/
├── src/main/kotlin/com/vernont/workflow/
│   ├── domain/              # Workflow execution entities
│   ├── repository/          # JPA repositories
│   ├── service/             # Business logic services
│   ├── engine/              # Core workflow engine
│   ├── config/              # Configuration classes
│   ├── scheduler/           # Maintenance tasks
│   ├── actuator/            # Health indicators
│   └── flows/               # Business workflow implementations
└── src/test/                # Comprehensive test suite
```

## 🔧 Configuration

### Application Configuration

```yaml
nexus:
  workflow:
    default-timeout-seconds: 300
    lock-timeout-seconds: 60
    max-retries: 3
    redis:
      address: redis://localhost:6379
      database: 0
    cleanup:
      enabled: true
      retention-days: 30
```

### Database Setup

The workflow engine requires PostgreSQL with the workflow executions table:

```sql
-- Auto-created via Flyway migration V10__create_workflow_execution_table.sql
CREATE TABLE workflow_executions (
    id VARCHAR(36) PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    -- ... additional columns
);
```

## 📝 Usage Examples

### 1. Register a Workflow

```kotlin
@Component
class OrderProcessingWorkflow(
    private val orderService: OrderService
) : Workflow<OrderInput, OrderOutput> {
    
    override val name = "process-order"
    
    override suspend fun execute(
        input: OrderInput, 
        context: WorkflowContext
    ): WorkflowResult<OrderOutput> {
        // Business logic here
        return WorkflowResult.success(OrderOutput(...))
    }
    
    override suspend fun compensate(context: WorkflowContext) {
        // Rollback logic
    }
}

// Registration (auto-discovered via @Component)
@PostConstruct
fun registerWorkflows() {
    workflowEngine.registerWorkflow(orderProcessingWorkflow, OrderInput::class, OrderOutput::class)
}
```

### 2. Execute a Workflow

```kotlin
@Service
class OrderController(
    private val workflowEngine: WorkflowEngine
) {
    
    suspend fun processOrder(orderData: OrderInput): OrderOutput {
        val result = workflowEngine.execute(
            workflowName = "process-order",
            input = orderData,
            inputType = OrderInput::class,
            outputType = OrderOutput::class,
            options = WorkflowOptions(
                correlationId = "order-${orderData.orderId}",
                timeoutSeconds = 600
            )
        )
        
        return result.getOrThrow()
    }
}
```

### 3. Monitor Executions

```kotlin
// Get execution status
val execution = workflowEngine.getExecution(executionId)
println("Status: ${execution.status}")

// Get workflow statistics
val stats = workflowEngine.getWorkflowStatistics("process-order", since = Instant.now().minus(1, ChronoUnit.HOURS))

// Retry failed execution
workflowEngine.retryExecution(executionId, OrderInput::class, OrderOutput::class)

### 4. Business Entity Locking

```kotlin
// Complete cart workflow with proper business locking
val result = workflowEngine.execute(
    workflowName = "complete-cart",
    input = CompleteCartInput(cartId = "cart_123"),
    inputType = CompleteCartInput::class,
    outputType = CompleteCartOutput::class,
    options = WorkflowOptions(
        correlationId = "cart:cart_123",
        lockKey = "cart:complete:cart_123",  // Prevents concurrent cart completions
        timeoutSeconds = 60
    )
)

// Multiple calls with same lockKey will block each other
// ✅ No double cart completions across distributed nodes
```

## 📊 Monitoring & Observability

### Health Checks

```bash
curl http://localhost:8080/actuator/health/workflow
```

### Metrics

The engine exposes comprehensive metrics:

- `workflow.executions.started`
- `workflow.executions.completed` 
- `workflow.executions.failed`
- `workflow.executions.timeout`
- `workflow.execution.duration`

### Logging

Structured logging with correlation IDs for distributed tracing:

```
2024-01-15 10:30:45 INFO  [WorkflowEngine] Starting workflow: process-order (execution: wf_exec_123)
2024-01-15 10:30:47 INFO  [WorkflowEngine] Workflow completed successfully: process-order (execution: wf_exec_123)
```

## 🔄 Maintenance & Operations

### Automatic Maintenance

The engine includes scheduled tasks for:

- **Timeout Handling**: Every minute
- **Auto-retry Failed Executions**: Every 5 minutes  
- **Cleanup Old Executions**: Daily at 2 AM
- **Statistics Logging**: Every hour

### Manual Operations

```kotlin
// Pause/Resume executions
workflowEngine.pauseExecution(executionId)
workflowEngine.resumeExecution<Input, Output>(executionId)

// Cancel execution
workflowEngine.cancelExecution(executionId)

// Manual cleanup
workflowExecutionService.cleanupOldExecutions(cutoffDate)
```

## 🧪 Testing

### Unit Tests

```bash
./gradlew :vernont-workflow:test
```

### Integration Tests

```bash
./gradlew :vernont-workflow:integrationTest
```

The test suite includes:
- Type safety validation
- Execution flow testing
- Failure and compensation scenarios
- Distributed locking behavior
- Timeout handling

## 🔒 Security Considerations

- **Distributed Locking**: Prevents concurrent execution conflicts
- **Input Validation**: Type-safe input validation
- **Audit Trail**: Complete execution history with user context
- **Error Handling**: Secure error messages without data leakage

## 📈 Performance

- **Connection Pooling**: Optimized database connections
- **Redis Integration**: Fast distributed coordination
- **Batch Processing**: Efficient database operations
- **Metrics Collection**: Low-overhead monitoring
- **Background Cleanup**: Non-blocking maintenance tasks

## 🚀 Production Deployment

1. **Database**: Ensure PostgreSQL with proper indexing
2. **Redis**: Configure Redis cluster for high availability
3. **Monitoring**: Set up Prometheus/Grafana for metrics
4. **Logging**: Configure centralized logging (ELK stack)
5. **Alerts**: Set up alerts for failed executions and timeouts

## 📋 Production Readiness Checklist

✅ **Persistence**: Database storage for all execution data  
✅ **Distribution**: Redis-based distributed locking  
✅ **Type Safety**: Compile-time type validation  
✅ **Error Handling**: Comprehensive exception handling with compensation  
✅ **Monitoring**: Metrics, health checks, and logging  
✅ **Testing**: Unit and integration test coverage  
✅ **Configuration**: Environment-specific configuration  
✅ **Maintenance**: Automatic cleanup and monitoring  
✅ **Documentation**: Complete usage and operational guides  
✅ **Security**: Input validation and audit trails  

## 🆚 Previous vs Production Version

| Feature | Previous | Production |
|---------|----------|------------|
| Storage | In-memory ConcurrentHashMap | PostgreSQL with JPA |
| Locking | None | Redis distributed locks |
| Type Safety | Unsafe casting | Compile-time validation |
| Monitoring | Basic logging | Metrics + Health checks |
| Retries | None | Configurable with backoff |
| Timeouts | None | Configurable per execution |
| Cleanup | Manual | Automatic scheduled |
| Testing | None | Comprehensive test suite |

The workflow engine is now **production-ready** and follows all established architectural patterns in the vernont-backend project! 🎉
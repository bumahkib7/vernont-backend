# NexusCommerce Monitoring Guide

This guide explains how to use Prometheus and Grafana for monitoring your NexusCommerce application.

## Quick Start

### Starting Services with Auto-Pull

Use the provided startup script to automatically pull latest images and start all services:

```bash
./docker-start.sh
```

This script will:
- Pull the latest Docker images for all services
- Start all containers in detached mode
- Show service status and recent logs

### Updating Services

To pull latest images and recreate containers:

```bash
./docker-update.sh
```

This will automatically:
- Pull new images
- Recreate containers with latest versions
- Clean up old images

## Access Points

Once started, you can access the following services:

| Service | URL | Credentials |
|---------|-----|-------------|
| API | http://localhost:8080 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3001 | admin/admin |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |
| Elasticsearch | http://localhost:9200 | - |
| Ollama (AI) | http://localhost:11434 | - |

## Prometheus

Prometheus collects metrics from the NexusCommerce API at `/actuator/prometheus`.

### Accessing Prometheus UI

1. Open http://localhost:9090
2. Go to **Status → Targets** to see all monitored endpoints
3. Use the **Graph** tab to query metrics

### Useful Prometheus Queries

**HTTP Request Rate:**
```promql
rate(http_server_requests_seconds_count{application="vernont"}[1m])
```

**JVM Memory Usage:**
```promql
jvm_memory_used_bytes{application="vernont"}
```

**Database Connection Pool:**
```promql
hikaricp_connections{application="vernont"}
```

**CPU Usage:**
```promql
process_cpu_usage{application="vernont"}
```

**HTTP Response Time (95th percentile):**
```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="vernont"}[1m])) by (le, uri, method))
```

## Grafana

Grafana provides visualization dashboards for your metrics.

### Initial Setup

1. Open http://localhost:3001
2. Login with `admin` / `admin`
3. (Optional) Change the password when prompted

### Pre-configured Dashboards

The **NexusCommerce - Spring Boot Overview** dashboard is automatically provisioned with panels for:

- HTTP Request Rate
- JVM Heap Usage
- JVM Memory Usage
- CPU Usage
- Database Connection Pool
- HTTP Request Duration (95th percentile)
- JVM Threads

### Creating Custom Dashboards

1. Click **+ → Dashboard** in the left sidebar
2. Click **Add new panel**
3. Select **Prometheus** as the data source
4. Enter your PromQL query
5. Customize visualization options
6. Save the dashboard

### Importing Dashboards

Grafana has a large community dashboard library:

1. Go to **+ → Import**
2. Enter dashboard ID (e.g., 4701 for JVM Micrometer)
3. Select **Prometheus** as the data source
4. Click **Import**

Popular Spring Boot dashboards:
- **4701** - JVM (Micrometer)
- **11378** - Spring Boot 2.1 Statistics
- **12900** - Spring Boot Observability

## Spring Boot Actuator Endpoints

The application exposes several actuator endpoints:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health status |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/metrics` | Available metrics |
| `/actuator/prometheus` | Prometheus-formatted metrics |
| `/actuator/info` | Application information |
| `/actuator/env` | Environment properties |
| `/actuator/loggers` | Logger configuration |

### Example Health Check

```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.4"
      }
    },
    "elasticsearch": {
      "status": "UP"
    }
  }
}
```

### Viewing Metrics

List all available metrics:
```bash
curl http://localhost:8080/actuator/metrics
```

Get specific metric:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

## Configuration

### Prometheus Configuration

Located at `monitoring/prometheus.yml`:

- **Scrape Interval**: 15 seconds
- **Evaluation Interval**: 15 seconds
- **Jobs**: Prometheus self-monitoring, NexusCommerce API

To add more scrape targets, edit this file and reload Prometheus:

```bash
docker-compose restart prometheus
```

### Grafana Configuration

Grafana provisioning is located in:
- **Datasources**: `monitoring/grafana/provisioning/datasources/`
- **Dashboards**: `monitoring/grafana/provisioning/dashboards/`
- **Dashboard JSON**: `monitoring/grafana/dashboards/`

Changes are automatically picked up within 30 seconds.

### Application Metrics

Configure metrics in `vernont-api/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
        step: 10s
```

## Alerting (Optional)

### Setting Up Alerts in Prometheus

1. Create alert rules in `monitoring/prometheus/alerts.yml`
2. Configure Alertmanager in `docker-compose.yml`
3. Update Prometheus configuration to include rules

Example alert rule:
```yaml
groups:
  - name: vernont
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[1m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "{{ $labels.uri }} has {{ $value }} 5xx errors"
```

### Setting Up Alerts in Grafana

1. Open a dashboard panel
2. Click **Alert** tab
3. Configure alert conditions
4. Set up notification channels (Slack, Email, PagerDuty, etc.)

## Troubleshooting

### Prometheus Can't Scrape Metrics

1. Check if actuator endpoint is accessible:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```

2. Verify Prometheus can reach the API:
   ```bash
   docker exec vernont-prometheus wget -O- http://vernont:8080/actuator/prometheus
   ```

3. Check Prometheus targets page: http://localhost:9090/targets

### Grafana Can't Connect to Prometheus

1. Verify Prometheus is running:
   ```bash
   docker ps | grep prometheus
   ```

2. Check datasource configuration in Grafana
3. Test connection: **Configuration → Data Sources → Prometheus → Test**

### No Data in Dashboards

1. Verify time range in Grafana (top-right corner)
2. Check if metrics exist in Prometheus
3. Verify PromQL queries are correct

## Performance Tuning

### Prometheus Storage

By default, Prometheus retains data for 15 days. To adjust:

```yaml
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'  # Keep 30 days
    - '--storage.tsdb.retention.size=10GB' # Max 10GB
```

### Grafana Performance

For better performance with large datasets:

1. Reduce dashboard refresh rate
2. Limit time ranges
3. Use recording rules in Prometheus for complex queries

## Backup and Recovery

### Prometheus Data

Prometheus data is stored in the `prometheus_data` Docker volume:

```bash
# Backup
docker run --rm -v vernont-backend_prometheus_data:/data -v $(pwd):/backup ubuntu tar czf /backup/prometheus-backup.tar.gz /data

# Restore
docker run --rm -v vernont-backend_prometheus_data:/data -v $(pwd):/backup ubuntu tar xzf /backup/prometheus-backup.tar.gz -C /
```

### Grafana Dashboards

Dashboards are automatically provisioned from `monitoring/grafana/dashboards/`.
Custom dashboards created in the UI are stored in the `grafana_data` volume.

Export dashboard: **Dashboard Settings → JSON Model → Copy to clipboard**

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)

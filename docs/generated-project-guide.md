# Generated project guide

Copy `.env.example` to a local environment file, replace placeholders, start dependencies with `docker compose up -d`, then run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Health probes are exposed below `/actuator/health`, Prometheus metrics under `/actuator/prometheus`, and OpenAPI UI under `/swagger-ui.html`.

Before deployment, run:

```shell
./mvnw verify
docker compose config
backsmith validate --project .
```

Production profiles use schema validation rather than destructive DDL. Configure database, identity-provider or JWT, Kafka, Redis, tracing, and CORS values through the deployment environment. Generated Kubernetes secrets are examples and must be replaced through your organization’s secret manager.

Kafka delivery and the transactional outbox are at-least-once. Consumers must keep idempotency enabled, and business handlers must remain safe to retry. Review retention, dead-event operations, alert thresholds, external-call timeouts, tenant scope, network exposure, and authorization policy for the target environment.

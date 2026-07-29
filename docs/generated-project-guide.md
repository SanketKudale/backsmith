# Generated project guide

Start PostgreSQL with `docker compose up -d`, then run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Health probes are exposed below `/actuator/health`, and OpenAPI UI is under `/swagger-ui.html`.

Review database credentials, network exposure, dependency versions, error handling, and security before deployment.

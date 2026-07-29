package io.backsmith.adapter.spring;

import io.backsmith.core.BackendFrameworkAdapter;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import io.backsmith.template.MustacheTemplateRenderer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SpringBootAdapter implements BackendFrameworkAdapter {
    private final MustacheTemplateRenderer renderer = new MustacheTemplateRenderer();

    @Override
    public String id() {
        return "spring";
    }

    @Override
    public Set<String> capabilities() {
        return Set.of("java-21", "maven", "postgresql", "flyway", "jpa", "openapi", "docker");
    }

    @Override
    public Map<Path, String> createProject(ProjectConfiguration configuration) {
        var project = configuration.project();
        var values = Map.<String, Object>of(
                "name", project.name(),
                "artifactId", project.artifactId(),
                "groupId", project.groupId(),
                "basePackage", project.basePackage(),
                "packagePath", project.basePackage().replace('.', '/'),
                "version", project.version(),
                "description", project.description(),
                "springVersion", configuration.runtime().frameworkVersion());
        var files = new LinkedHashMap<Path, String>();
        put(files, "pom.xml", POM, values);
        put(files, "backsmith.yaml", configurationYaml(configuration), values);
        put(files, ".gitignore", GENERATED_GITIGNORE, values);
        put(files, ".editorconfig", EDITORCONFIG, values);
        put(files, ".mvn/wrapper/maven-wrapper.properties", WRAPPER_PROPERTIES, values);
        put(files, "mvnw", GENERATED_MVNW, values);
        put(files, "mvnw.cmd", GENERATED_MVNW_CMD, values);
        put(files, ".env.example", ENV, values);
        put(files, "Dockerfile", DOCKERFILE, values);
        put(files, "compose.yaml", COMPOSE, values);
        put(files, ".github/workflows/build.yml", WORKFLOW, values);
        put(files, "README.md", GENERATED_README, values);
        put(files, "src/main/java/{{packagePath}}/Application.java", APPLICATION, values);
        put(files, "src/main/resources/application.yml", APPLICATION_YAML, values);
        put(files, "src/main/resources/application-local.yml", LOCAL_YAML, values);
        put(files, "src/main/resources/application-test.yml", TEST_YAML, values);
        put(files, "src/main/resources/application-prod.yml", PROD_YAML, values);
        put(files, "src/main/resources/db/migration/V1__initial_schema.sql", MIGRATION, values);
        put(files, "src/main/java/{{packagePath}}/shared/error/ApiExceptionHandler.java", ERROR_HANDLER, values);
        put(files, "src/main/java/{{packagePath}}/shared/web/CorrelationIdFilter.java", CORRELATION_FILTER, values);
        if (configuration.architecture().type() == Architecture.HEXAGONAL) {
            addHexagonal(files, values);
        } else {
            addLayered(files, values);
        }
        put(files, "src/test/java/{{packagePath}}/ApplicationTest.java", APPLICATION_TEST, values);
        put(files, ".backsmith/manifest.json", MANIFEST, values);
        return Map.copyOf(files);
    }

    private void addLayered(Map<Path, String> files, Map<String, Object> values) {
        put(files, "src/main/java/{{packagePath}}/modules/sample/entity/Greeting.java", GREETING_ENTITY, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/repository/GreetingRepository.java", GREETING_REPOSITORY, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/service/GreetingService.java", GREETING_SERVICE, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/controller/GreetingController.java", GREETING_CONTROLLER, values);
    }

    private void addHexagonal(Map<Path, String> files, Map<String, Object> values) {
        put(files, "src/main/java/{{packagePath}}/modules/sample/domain/model/Greeting.java", DOMAIN_GREETING, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/application/port/in/GetGreetingUseCase.java", INPUT_PORT, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/application/service/GreetingService.java", HEX_SERVICE, values);
        put(files, "src/main/java/{{packagePath}}/modules/sample/adapter/in/web/GreetingController.java", HEX_CONTROLLER, values);
    }

    private void put(Map<Path, String> files, String pathTemplate, String template, Map<String, Object> values) {
        files.put(Path.of(renderer.render(pathTemplate, values).trim()), renderer.render(template, values));
    }

    private String configurationYaml(ProjectConfiguration configuration) {
        return """
                schema_version: 1
                project:
                  name: {{name}}
                  group_id: {{groupId}}
                  artifact_id: {{artifactId}}
                  base_package: {{basePackage}}
                  version: {{version}}
                  description: {{description}}
                runtime:
                  language: java
                  java_version: 21
                  framework: spring
                  framework_version: "{{springVersion}}"
                  build_tool: maven
                architecture:
                  type: %s
                  enforce_boundaries: true
                features:
                  database: postgresql
                  persistence: jpa
                  migrations: flyway
                  security: %s
                  messaging: %s
                  cache: %s
                  observability: %s
                  openapi: true
                  docker: true
                  tests: true
                modules:
                  - sample
                """.formatted(
                configuration.architecture().type().name(),
                configuration.features().security(),
                configuration.features().messaging(),
                configuration.features().cache(),
                configuration.features().observability());
    }

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>{{springVersion}}</version><relativePath/></parent>
              <groupId>{{groupId}}</groupId><artifactId>{{artifactId}}</artifactId><version>{{version}}</version>
              <name>{{name}}</name><description>{{description}}</description>
              <properties><java.version>21</java.version></properties>
              <dependencies>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
                <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
                <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.9</version></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
              </dependencies>
              <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
            </project>
            """;
    private static final String APPLICATION = """
            package {{basePackage}};

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;

            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
            """;
    private static final String APPLICATION_YAML = """
            spring:
              application:
                name: {{name}}
              datasource:
                url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/{{artifactId}}}
                username: ${DATABASE_USERNAME:postgres}
                password: ${DATABASE_PASSWORD:postgres}
              jpa:
                open-in-view: false
                hibernate:
                  ddl-auto: validate
              lifecycle:
                timeout-per-shutdown-phase: 20s
            server:
              shutdown: graceful
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics,prometheus
              endpoint:
                health:
                  probes:
                    enabled: true
            """;
    private static final String LOCAL_YAML = "logging:\n  level:\n    root: INFO\n    {{basePackage}}: DEBUG\n";
    private static final String TEST_YAML = "spring:\n  main:\n    banner-mode: off\n";
    private static final String PROD_YAML = "server:\n  forward-headers-strategy: framework\n";
    private static final String MIGRATION = """
            CREATE TABLE greeting (
              id UUID PRIMARY KEY,
              message VARCHAR(255) NOT NULL,
              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            """;
    private static final String GREETING_ENTITY = """
            package {{basePackage}}.modules.sample.entity;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import java.util.UUID;
            @Entity
            public class Greeting {
                @Id private UUID id;
                private String message;
                protected Greeting() {}
                public Greeting(UUID id, String message) { this.id = id; this.message = message; }
                public UUID getId() { return id; }
                public String getMessage() { return message; }
            }
            """;
    private static final String GREETING_REPOSITORY = """
            package {{basePackage}}.modules.sample.repository;
            import {{basePackage}}.modules.sample.entity.Greeting;
            import java.util.UUID;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface GreetingRepository extends JpaRepository<Greeting, UUID> {}
            """;
    private static final String GREETING_SERVICE = """
            package {{basePackage}}.modules.sample.service;
            import {{basePackage}}.modules.sample.entity.Greeting;
            import {{basePackage}}.modules.sample.repository.GreetingRepository;
            import java.util.UUID;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            @Service
            public class GreetingService {
                private final GreetingRepository repository;
                public GreetingService(GreetingRepository repository) { this.repository = repository; }
                @Transactional
                public Greeting create(String message) { return repository.save(new Greeting(UUID.randomUUID(), message)); }
            }
            """;
    private static final String GREETING_CONTROLLER = """
            package {{basePackage}}.modules.sample.controller;
            import {{basePackage}}.modules.sample.entity.Greeting;
            import {{basePackage}}.modules.sample.service.GreetingService;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.NotBlank;
            import org.springframework.http.HttpStatus;
            import org.springframework.web.bind.annotation.*;
            @RestController @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final GreetingService service;
                public GreetingController(GreetingService service) { this.service = service; }
                @PostMapping @ResponseStatus(HttpStatus.CREATED)
                public GreetingResponse create(@Valid @RequestBody CreateGreeting request) {
                    Greeting greeting = service.create(request.message());
                    return new GreetingResponse(greeting.getId().toString(), greeting.getMessage());
                }
                public record CreateGreeting(@NotBlank String message) {}
                public record GreetingResponse(String id, String message) {}
            }
            """;
    private static final String DOMAIN_GREETING = """
            package {{basePackage}}.modules.sample.domain.model;
            import java.util.Objects;
            import java.util.UUID;
            public record Greeting(UUID id, String message) {
                public Greeting { Objects.requireNonNull(id); if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank"); }
            }
            """;
    private static final String INPUT_PORT = """
            package {{basePackage}}.modules.sample.application.port.in;
            import {{basePackage}}.modules.sample.domain.model.Greeting;
            public interface GetGreetingUseCase { Greeting greeting(String message); }
            """;
    private static final String HEX_SERVICE = """
            package {{basePackage}}.modules.sample.application.service;
            import {{basePackage}}.modules.sample.application.port.in.GetGreetingUseCase;
            import {{basePackage}}.modules.sample.domain.model.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Service;
            @Service
            public class GreetingService implements GetGreetingUseCase {
                public Greeting greeting(String message) { return new Greeting(UUID.randomUUID(), message); }
            }
            """;
    private static final String HEX_CONTROLLER = """
            package {{basePackage}}.modules.sample.adapter.in.web;
            import {{basePackage}}.modules.sample.application.port.in.GetGreetingUseCase;
            import jakarta.validation.constraints.NotBlank;
            import org.springframework.web.bind.annotation.*;
            @RestController @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final GetGreetingUseCase useCase;
                public GreetingController(GetGreetingUseCase useCase) { this.useCase = useCase; }
                @GetMapping public Object get(@RequestParam @NotBlank String message) { return useCase.greeting(message); }
            }
            """;
    private static final String ERROR_HANDLER = """
            package {{basePackage}}.shared.error;
            import org.springframework.http.*;
            import org.springframework.web.bind.MethodArgumentNotValidException;
            import org.springframework.web.bind.annotation.*;
            @RestControllerAdvice
            public class ApiExceptionHandler {
                @ExceptionHandler(MethodArgumentNotValidException.class)
                ProblemDetail validation(MethodArgumentNotValidException exception) {
                    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
                }
            }
            """;
    private static final String CORRELATION_FILTER = """
            package {{basePackage}}.shared.web;
            import jakarta.servlet.*;
            import jakarta.servlet.http.*;
            import java.io.IOException;
            import java.util.UUID;
            import org.springframework.stereotype.Component;
            @Component
            public class CorrelationIdFilter extends org.springframework.web.filter.OncePerRequestFilter {
                private static final String HEADER = "X-Correlation-Id";
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    String id = request.getHeader(HEADER);
                    response.setHeader(HEADER, id == null || id.isBlank() ? UUID.randomUUID().toString() : id);
                    chain.doFilter(request, response);
                }
            }
            """;
    private static final String APPLICATION_TEST = """
            package {{basePackage}};
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
            class ApplicationTest {
                @Test void applicationClassLoads() { assertDoesNotThrow(() -> Class.forName("{{basePackage}}.Application")); }
            }
            """;
    private static final String DOCKERFILE = """
            FROM eclipse-temurin:21-jdk AS build
            WORKDIR /workspace
            COPY . .
            RUN ./mvnw -B -DskipTests package
            FROM eclipse-temurin:21-jre
            RUN useradd --system --uid 10001 app
            USER app
            COPY --from=build /workspace/target/*.jar /app.jar
            EXPOSE 8080
            ENTRYPOINT ["java","-jar","/app.jar"]
            """;
    private static final String COMPOSE = """
            services:
              postgres:
                image: postgres:17-alpine
                environment:
                  POSTGRES_DB: {{artifactId}}
                  POSTGRES_USER: postgres
                  POSTGRES_PASSWORD: postgres
                ports: ["5432:5432"]
                healthcheck:
                  test: ["CMD-SHELL", "pg_isready -U postgres -d {{artifactId}}"]
                  interval: 5s
                  timeout: 3s
                  retries: 10
                volumes: [postgres-data:/var/lib/postgresql/data]
            volumes:
              postgres-data:
            """;
    private static final String WORKFLOW = """
            name: build
            on:
              push:
              pull_request:
            permissions:
              contents: read
            jobs:
              verify:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
                  - uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
                    with:
                      distribution: temurin
                      java-version: "21"
                      cache: maven
                  - run: ./mvnw -B verify
            """;
    private static final String GENERATED_README = """
            # {{name}}

            Generated by Backsmith. Review generated code and security settings before production use.

            ## Local development

            ```shell
            docker compose up -d
            ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
            ```

            OpenAPI UI is available at `http://localhost:8080/swagger-ui.html`; health is at `/actuator/health`.
            """;
    private static final String GENERATED_GITIGNORE = "target/\n.idea/\n.env\n*.log\n";
    private static final String EDITORCONFIG = "root = true\n\n[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\nindent_style = space\nindent_size = 2\n";
    private static final String WRAPPER_PROPERTIES = "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip\n";
    private static final String GENERATED_MVNW = """
            #!/usr/bin/env sh
            set -eu
            ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
            MVN_HOME="$ROOT/.mvn/apache-maven-3.9.11"
            if [ ! -x "$MVN_HOME/bin/mvn" ]; then
              ARCHIVE="${TMPDIR:-/tmp}/backsmith-maven-3.9.11.tar.gz"
              curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz" -o "$ARCHIVE"
              tar -xzf "$ARCHIVE" -C "$ROOT/.mvn"
              rm -f "$ARCHIVE"
            fi
            exec "$MVN_HOME/bin/mvn" "$@"
            """;
    private static final String GENERATED_MVNW_CMD = """
            @echo off
            setlocal
            set "ROOT=%~dp0"
            set "MVN_HOME=%ROOT%.mvn\\apache-maven-3.9.11"
            if not exist "%MVN_HOME%\\bin\\mvn.cmd" (
              powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP ('maven-' + [guid]::NewGuid() + '.zip'); try { Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile $zip; Expand-Archive -Force $zip '%ROOT%.mvn' } finally { Remove-Item -ErrorAction SilentlyContinue $zip }"
              if errorlevel 1 exit /b 1
            )
            call "%MVN_HOME%\\bin\\mvn.cmd" %*
            """;
    private static final String ENV = "DATABASE_URL=jdbc:postgresql://localhost:5432/{{artifactId}}\nDATABASE_USERNAME=postgres\nDATABASE_PASSWORD=change-me\n";
    private static final String MANIFEST = """
            {
              "schemaVersion": 1,
              "backsmithVersion": "0.1.1",
              "generator": "spring-project",
              "ownership": "fully-managed",
              "note": "Per-file hashes are populated by a future manifest upgrade."
            }
            """;
}

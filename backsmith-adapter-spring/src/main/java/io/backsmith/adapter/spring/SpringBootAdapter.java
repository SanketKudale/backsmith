package io.backsmith.adapter.spring;

import io.backsmith.core.BackendFrameworkAdapter;
import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.FrameworkCapabilities;
import io.backsmith.core.GeneratorProvider;
import io.backsmith.model.ProjectConfiguration;
import io.backsmith.template.ClasspathTemplateCatalog;
import io.backsmith.template.MustacheTemplateRenderer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpringBootAdapter implements BackendFrameworkAdapter {
    private final MustacheTemplateRenderer renderer = new MustacheTemplateRenderer();
    private final ClasspathTemplateCatalog templates =
            new ClasspathTemplateCatalog(
                    SpringBootAdapter.class.getClassLoader(), "templates/spring/1");

    @Override
    public String id() {
        return "spring";
    }

    @Override
    public FrameworkCapabilities capabilities() {
        return new FrameworkCapabilities(
                Set.of("java-21", "spring-boot-3", "maven"),
                Set.of(
                        "layered",
                        "hexagonal",
                        "modular-monolith",
                        "clean",
                        "onion",
                        "cqrs",
                        "microservice"),
                Set.of("postgresql", "jpa", "flyway"),
                Set.of("none", "basic", "session", "jwt", "oauth2", "oidc"),
                Set.of("none", "kafka"),
                Set.of("none", "redis"),
                Set.of("docker", "docker-compose", "kubernetes"));
    }

    @Override
    public List<GeneratorProvider> generators() {
        return List.of(
                new GeneratorProvider("entity", Set.of("module", "field", "tests", "architecture")),
                new GeneratorProvider("api", Set.of("module", "contract", "tests")),
                new GeneratorProvider(
                        "integration", Set.of("module", "type", "tests", "resilience")),
                new GeneratorProvider("module", Set.of("architecture", "tests")));
    }

    @Override
    public Map<Path, String> createProject(ProjectConfiguration configuration) {
        var project = configuration.project();
        var values = new LinkedHashMap<String, Object>();
        values.put("name", project.name());
        values.put("artifactId", project.artifactId());
        values.put("groupId", project.groupId());
        values.put("basePackage", project.basePackage());
        values.put("packagePath", project.basePackage().replace('.', '/'));
        values.put("version", project.version());
        values.put("description", project.description());
        values.put("springVersion", configuration.runtime().frameworkVersion());
        values.put("securityEnabled", !configuration.security().mode().equalsIgnoreCase("none"));
        values.put("sessionSecurity", configuration.security().mode().equalsIgnoreCase("session"));
        values.put(
                "resourceServer",
                Set.of("jwt", "oauth2", "oidc")
                        .contains(
                                configuration
                                        .security()
                                        .mode()
                                        .toLowerCase(java.util.Locale.ROOT)));
        values.put("kafka", configuration.messaging().provider().equalsIgnoreCase("kafka"));
        values.put("outbox", configuration.messaging().outbox());
        values.put("redis", configuration.cache().enabled());
        values.put("tracing", configuration.observability().tracing());
        values.put("structuredLogging", configuration.observability().structuredLogging());
        values.put(
                "resilience",
                configuration.resilience().circuitBreaker()
                        || configuration.resilience().retry()
                        || configuration.resilience().timeout()
                        || configuration.resilience().rateLimiter()
                        || configuration.resilience().bulkhead());
        values.put("testcontainers", configuration.testing().testcontainers());
        values.put("architectureTests", configuration.testing().architecture());
        values.put("failOnWarning", configuration.generation().failOnWarning());
        values.put("kubernetes", configuration.deployment().kubernetes());
        values.put("multiTenancy", !configuration.multiTenancy().mode().equalsIgnoreCase("none"));
        values.put("starterAuth", configuration.modules().contains("authentication"));
        values.put("idempotency", configuration.api().idempotency());
        values.put("starterSample", configuration.modules().contains("sample"));
        values.put(
                "layered",
                configuration.architecture().type() == io.backsmith.model.Architecture.LAYERED);
        values.put(
                "modularMonolith",
                configuration.architecture().type()
                        == io.backsmith.model.Architecture.MODULAR_MONOLITH);
        values.put("localJwt", configuration.modules().contains("authentication"));
        values.put(
                "externalResourceServer",
                (boolean) values.get("resourceServer")
                        && !configuration.modules().contains("authentication"));
        var files = new LinkedHashMap<Path, String>();
        put(files, "pom.xml", POM, values);
        put(files, "backsmith.yaml", configurationYaml(configuration), values);
        put(files, ".gitignore", GENERATED_GITIGNORE, values);
        put(files, ".dockerignore", DOCKERIGNORE, values);
        put(files, ".editorconfig", EDITORCONFIG, values);
        put(files, "config/checkstyle/checkstyle.xml", GENERATED_CHECKSTYLE, values);
        put(files, ".mvn/wrapper/maven-wrapper.properties", WRAPPER_PROPERTIES, values);
        put(files, "mvnw", GENERATED_MVNW, values);
        put(files, "mvnw.cmd", GENERATED_MVNW_CMD, values);
        put(files, ".env.example", ENV, values);
        put(files, "Dockerfile", DOCKERFILE, values);
        put(files, "compose.yaml", COMPOSE, values);
        put(files, ".github/workflows/build.yml", WORKFLOW, values);
        put(files, "README.md", templates.load("common/readme.mustache"), values);
        put(files, "src/main/java/{{packagePath}}/Application.java", APPLICATION, values);
        put(files, "src/main/resources/application.yml", APPLICATION_YAML, values);
        put(files, "src/main/resources/application-local.yml", LOCAL_YAML, values);
        put(files, "src/main/resources/application-test.yml", TEST_YAML, values);
        put(files, "src/main/resources/application-prod.yml", PROD_YAML, values);
        put(files, "src/main/resources/db/migration/V1__initial_schema.sql", MIGRATION, values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/error/ApiExceptionHandler.java",
                ERROR_HANDLER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/web/CorrelationIdFilter.java",
                CORRELATION_FILTER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/configuration/TimeConfiguration.java",
                TIME_CONFIGURATION,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/configuration/ApplicationProperties.java",
                APPLICATION_PROPERTIES,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/web/IdempotencyKey.java",
                IDEMPOTENCY_KEY,
                values);
        if (configuration.api().idempotency()) {
            addIdempotency(files, values);
        }
        put(
                files,
                "src/main/java/{{packagePath}}/shared/observability/AuditEvent.java",
                AUDIT_EVENT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/observability/AuditLogger.java",
                AUDIT_LOGGER,
                values);
        if ((boolean) values.get("securityEnabled")) {
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/security/SecurityConfiguration.java",
                    SECURITY_CONFIGURATION,
                    values);
        }
        if ((boolean) values.get("starterAuth")) {
            addStarterAuthentication(files, values);
        }
        if (configuration.modules().contains("customer")) {
            addCustomerStarter(files, values);
        }
        if (configuration.modules().contains("payment")) {
            addPaymentStarter(files, values);
        }
        if ((boolean) values.get("kafka")) {
            addMessaging(files, values, (boolean) values.get("outbox"));
        }
        if ((boolean) values.get("redis")) {
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/cache/CacheKeyFactory.java",
                    CACHE_KEY_FACTORY,
                    values);
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/cache/RedisCacheStore.java",
                    REDIS_CACHE_STORE,
                    values);
            put(
                    files,
                    "src/test/java/{{packagePath}}/shared/cache/CacheKeyFactoryTest.java",
                    CACHE_KEY_FACTORY_TEST,
                    values);
        }
        if ((boolean) values.get("multiTenancy")) {
            addMultiTenancy(files, values);
        }
        if (configuration.modules().contains("sample")) {
            switch (configuration.architecture().type()) {
                case LAYERED -> addLayered(files, values);
                case HEXAGONAL, MICROSERVICE -> addHexagonal(files, values);
                case MODULAR_MONOLITH -> addModularMonolith(files, values);
                case CLEAN -> addClean(files, values);
                case ONION -> addOnion(files, values);
                case CQRS -> addCqrs(files, values);
            }
        }
        put(files, "src/test/java/{{packagePath}}/ApplicationTest.java", APPLICATION_TEST, values);
        if ((boolean) values.get("architectureTests")) {
            put(
                    files,
                    "src/test/java/{{packagePath}}/ArchitectureTest.java",
                    ARCHITECTURE_TEST,
                    values);
        }
        if ((boolean) values.get("testcontainers")) {
            put(
                    files,
                    "src/test/java/{{packagePath}}/PostgreSqlIntegrationTest.java",
                    POSTGRES_INTEGRATION_TEST,
                    values);
        }
        if ((boolean) values.get("kubernetes")) {
            addKubernetes(files, values);
        }
        return Map.copyOf(files);
    }

    private void addStarterAuthentication(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/authentication/domain/Account.java",
                AUTH_ACCOUNT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/authentication/domain/Role.java",
                AUTH_ROLE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/authentication/application/AuthenticationService.java",
                AUTH_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/authentication/infrastructure/JpaAuthenticationService.java",
                AUTH_JPA_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/authentication/api/AuthenticationController.java",
                AUTH_CONTROLLER,
                values);
        put(
                files,
                "src/main/resources/db/migration/V2__authentication.sql",
                AUTH_MIGRATION,
                values);
    }

    private void addCustomerStarter(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/customer/domain/Customer.java",
                CUSTOMER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/customer/application/CustomerService.java",
                CUSTOMER_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/customer/api/CustomerController.java",
                CUSTOMER_CONTROLLER,
                values);
        put(files, "src/main/resources/db/migration/V5__customer.sql", CUSTOMER_MIGRATION, values);
    }

    private void addPaymentStarter(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/payment/domain/Money.java",
                PAYMENT_MONEY,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/payment/domain/PaymentStatus.java",
                PAYMENT_STATUS,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/payment/domain/Payment.java",
                PAYMENT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/payment/application/PaymentService.java",
                PAYMENT_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/payment/api/PaymentController.java",
                PAYMENT_CONTROLLER,
                values);
        put(files, "src/main/resources/db/migration/V6__payment.sql", PAYMENT_MIGRATION, values);
        put(
                files,
                "src/test/java/{{packagePath}}/modules/payment/domain/PaymentTest.java",
                PAYMENT_TEST,
                values);
    }

    private void addMessaging(Map<Path, String> files, Map<String, Object> values, boolean outbox) {
        put(
                files,
                "src/main/java/{{packagePath}}/shared/messaging/EventEnvelope.java",
                EVENT_ENVELOPE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/messaging/KafkaEventPublisher.java",
                KAFKA_EVENT_PUBLISHER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/messaging/IdempotentEventStore.java",
                IDEMPOTENT_EVENT_STORE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/messaging/JdbcIdempotentEventStore.java",
                JDBC_IDEMPOTENT_EVENT_STORE,
                values);
        put(
                files,
                "src/main/resources/db/migration/V3__messaging_delivery.sql",
                CONSUMED_EVENT_MIGRATION,
                values);
        if (outbox) {
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/messaging/outbox/OutboxEvent.java",
                    OUTBOX_EVENT,
                    values);
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/messaging/outbox/OutboxRepository.java",
                    OUTBOX_REPOSITORY,
                    values);
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/messaging/outbox/OutboxPublisher.java",
                    OUTBOX_PUBLISHER,
                    values);
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/messaging/outbox/TransactionalOutbox.java",
                    TRANSACTIONAL_OUTBOX,
                    values);
            put(
                    files,
                    "src/main/java/{{packagePath}}/shared/messaging/outbox/OutboxCleanup.java",
                    OUTBOX_CLEANUP,
                    values);
            put(
                    files,
                    "src/test/java/{{packagePath}}/shared/messaging/outbox/OutboxEventTest.java",
                    OUTBOX_TEST,
                    values);
            put(
                    files,
                    "src/main/resources/db/migration/V4__transactional_outbox.sql",
                    OUTBOX_MIGRATION,
                    values);
        }
    }

    private void addIdempotency(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/shared/idempotency/IdempotencyService.java",
                IDEMPOTENCY_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/idempotency/IdempotencyCleanup.java",
                IDEMPOTENCY_CLEANUP,
                values);
        put(
                files,
                "src/main/resources/db/migration/V7__idempotency.sql",
                IDEMPOTENCY_MIGRATION,
                values);
    }

    private void addMultiTenancy(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/TenantContext.java",
                TENANT_CONTEXT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/TenantResolver.java",
                TENANT_RESOLVER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/HeaderTenantResolver.java",
                HEADER_TENANT_RESOLVER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/TenantMembership.java",
                TENANT_MEMBERSHIP,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/JwtTenantMembership.java",
                JWT_TENANT_MEMBERSHIP,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/shared/tenancy/TenantFilter.java",
                TENANT_FILTER,
                values);
        put(
                files,
                "src/test/java/{{packagePath}}/shared/tenancy/JwtTenantMembershipTest.java",
                JWT_TENANT_MEMBERSHIP_TEST,
                values);
    }

    private void addKubernetes(Map<Path, String> files, Map<String, Object> values) {
        put(files, "deploy/kubernetes/deployment.yaml", KUBERNETES_DEPLOYMENT, values);
        put(files, "deploy/kubernetes/service.yaml", KUBERNETES_SERVICE, values);
        put(files, "deploy/kubernetes/configmap.yaml", KUBERNETES_CONFIGMAP, values);
        put(files, "deploy/kubernetes/secret.example.yaml", KUBERNETES_SECRET, values);
        put(files, "deploy/kubernetes/hpa.yaml", KUBERNETES_HPA, values);
        put(files, "deploy/kubernetes/pdb.yaml", KUBERNETES_PDB, values);
        put(files, "deploy/kubernetes/ingress.yaml", KUBERNETES_INGRESS, values);
    }

    private void addLayered(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/entity/Greeting.java",
                GREETING_ENTITY,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/repository/GreetingRepository.java",
                GREETING_REPOSITORY,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/service/GreetingService.java",
                GREETING_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/controller/GreetingController.java",
                GREETING_CONTROLLER,
                values);
    }

    private void addHexagonal(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/model/Greeting.java",
                DOMAIN_GREETING,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/port/in/GetGreetingUseCase.java",
                INPUT_PORT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/service/GreetingService.java",
                HEX_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/adapter/in/web/GreetingController.java",
                HEX_CONTROLLER,
                values);
    }

    private void addModularMonolith(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/Greeting.java",
                DOMAIN_GREETING.replace(".domain.model", ".domain"),
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/GreetingService.java",
                MODULAR_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/api/GreetingController.java",
                MODULAR_CONTROLLER,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/package-info.java",
                MODULAR_PACKAGE_INFO,
                values);
    }

    private void addClean(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/Greeting.java",
                DOMAIN_GREETING.replace(".domain.model", ".domain"),
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/GetGreeting.java",
                CLEAN_USE_CASE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/interfaceadapters/web/GreetingController.java",
                CLEAN_CONTROLLER,
                values);
    }

    private void addOnion(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/Greeting.java",
                DOMAIN_GREETING.replace(".domain.model", ".domain"),
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/GreetingApplicationService.java",
                ONION_SERVICE,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/api/GreetingController.java",
                ONION_CONTROLLER,
                values);
    }

    private void addCqrs(Map<Path, String> files, Map<String, Object> values) {
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/Greeting.java",
                DOMAIN_GREETING.replace(".domain.model", ".domain"),
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/command/CommandBus.java",
                COMMAND_BUS,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/query/QueryBus.java",
                QUERY_BUS,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/query/GetGreetingQuery.java",
                GET_GREETING_QUERY,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/command/CreateGreetingCommand.java",
                CREATE_GREETING_COMMAND,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/application/query/GreetingProjection.java",
                GREETING_PROJECTION,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/domain/GreetingCreated.java",
                GREETING_CREATED_EVENT,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/infrastructure/SimpleCommandBus.java",
                SIMPLE_COMMAND_BUS,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/infrastructure/SimpleQueryBus.java",
                SIMPLE_QUERY_BUS,
                values);
        put(
                files,
                "src/main/java/{{packagePath}}/modules/sample/api/GreetingController.java",
                CQRS_CONTROLLER,
                values);
    }

    private void put(
            Map<Path, String> files,
            String pathTemplate,
            String template,
            Map<String, Object> values) {
        files.put(
                Path.of(renderer.render(pathTemplate, values).trim()),
                renderer.render(template, values));
    }

    private String configurationYaml(ProjectConfiguration configuration) {
        try {
            return new ConfigurationCodec().write(configuration);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("unable to serialize backsmith.yaml", exception);
        }
    }

    private static final String POM =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>{{springVersion}}</version><relativePath/></parent>
              <groupId>{{groupId}}</groupId><artifactId>{{artifactId}}</artifactId><version>{{version}}</version>
              <name>{{name}}</name><description>{{description}}</description>
              <properties>
                <java.version>21</java.version>
                <maven.compiler.showWarnings>true</maven.compiler.showWarnings>
                <maven.compiler.failOnWarning>{{failOnWarning}}</maven.compiler.failOnWarning>
                <project.build.outputTimestamp>2026-07-29T00:00:00Z</project.build.outputTimestamp>
              </properties>
              <dependencies>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
                <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
                <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.17</version></dependency>
                <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId><scope>runtime</scope></dependency>
                {{#securityEnabled}}
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
                {{/securityEnabled}}
                {{#resourceServer}}
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
                {{/resourceServer}}
                {{#kafka}}
                <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>
                <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka-test</artifactId><scope>test</scope></dependency>
                {{/kafka}}
                {{#redis}}
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
                {{/redis}}
                {{#tracing}}
                <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-tracing-bridge-otel</artifactId></dependency>
                <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-exporter-otlp</artifactId></dependency>
                {{/tracing}}
                {{#resilience}}
                <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId><version>2.4.0</version></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-aop</artifactId></dependency>
                {{/resilience}}
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                {{#testcontainers}}
                <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><version>2.0.5</version><scope>test</scope></dependency>
                <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><version>2.0.5</version><scope>test</scope></dependency>
                {{/testcontainers}}
                {{#architectureTests}}
                <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId><version>1.4.1</version><scope>test</scope></dependency>
                {{/architectureTests}}
              </dependencies>
              <build>
                <plugins>
                  <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId><version>3.6.3</version>
                    <executions><execution><goals><goal>enforce</goal></goals><configuration><rules>
                      <requireMavenVersion><version>[3.9.0,)</version></requireMavenVersion>
                      <requireJavaVersion><version>[21,22)</version></requireJavaVersion>
                      <dependencyConvergence/>
                    </rules></configuration></execution></executions>
                  </plugin>
                  <plugin>
                    <groupId>com.diffplug.spotless</groupId><artifactId>spotless-maven-plugin</artifactId><version>3.6.0</version>
                    <configuration><lineEndings>UNIX</lineEndings><java><trimTrailingWhitespace/><endWithNewline/></java></configuration>
                    <executions><execution><phase>verify</phase><goals><goal>check</goal></goals></execution></executions>
                  </plugin>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-checkstyle-plugin</artifactId><version>3.6.0</version>
                    <configuration><configLocation>config/checkstyle/checkstyle.xml</configLocation><includeTestSourceDirectory>true</includeTestSourceDirectory></configuration>
                    <executions><execution><phase>verify</phase><goals><goal>check</goal></goals></execution></executions>
                  </plugin>
                  <plugin>
                    <groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.15</version>
                    <executions>
                      <execution><goals><goal>prepare-agent</goal></goals></execution>
                      <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
                    </executions>
                  </plugin>
                </plugins>
              </build>
              <profiles>
                <profile>
                  <id>security</id>
                  <build><plugins><plugin>
                    <groupId>org.owasp</groupId><artifactId>dependency-check-maven</artifactId><version>12.2.2</version>
                    <configuration><failBuildOnCVSS>7</failBuildOnCVSS></configuration>
                    <executions><execution><goals><goal>check</goal></goals></execution></executions>
                  </plugin></plugins></build>
                </profile>
              </profiles>
            </project>
            """;
    private static final String APPLICATION =
            """
            package {{basePackage}};

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
            import org.springframework.scheduling.annotation.EnableScheduling;

            @SpringBootApplication
            @ConfigurationPropertiesScan
            @EnableScheduling
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
            """;
    private static final String APPLICATION_YAML =
            """
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
              {{#kafka}}
              kafka:
                bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
                producer:
                  acks: all
                  properties:
                    enable.idempotence: true
                consumer:
                  enable-auto-commit: false
                  auto-offset-reset: earliest
              {{/kafka}}
              {{#redis}}
              data:
                redis:
                  host: ${REDIS_HOST:localhost}
                  port: ${REDIS_PORT:6379}
              {{/redis}}
              {{#resourceServer}}
              security:
                oauth2:
                  resourceserver:
                    jwt:
                      {{#externalResourceServer}}
                      issuer-uri: ${JWT_ISSUER_URI}
                      {{/externalResourceServer}}
                      audiences: ${JWT_AUDIENCE:{{artifactId}}}
              {{/resourceServer}}
            {{#localJwt}}
            app:
              jwt:
                secret: ${JWT_SECRET}
                audience: ${JWT_AUDIENCE:{{artifactId}}}
                access-token-ttl: ${JWT_ACCESS_TOKEN_TTL:PT15M}
                refresh-token-ttl: ${JWT_REFRESH_TOKEN_TTL:P30D}
            {{/localJwt}}
            server:
              shutdown: graceful
            {{#structuredLogging}}
            logging:
              structured:
                format:
                  console: ecs
            {{/structuredLogging}}
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics,prometheus
              endpoint:
                health:
                  probes:
                    enabled: true
            springdoc:
              swagger-ui:
                path: /swagger-ui.html
            {{#resilience}}
            resilience4j:
              circuitbreaker:
                instances:
                  external:
                    sliding-window-size: 20
                    failure-rate-threshold: 50
              retry:
                instances:
                  external:
                    max-attempts: 3
                    wait-duration: 250ms
                    enable-exponential-backoff: true
              timelimiter:
                instances:
                  external:
                    timeout-duration: 3s
            {{/resilience}}
            """;
    private static final String LOCAL_YAML =
            "logging:\n  level:\n    root: INFO\n    {{basePackage}}: DEBUG\n";
    private static final String TEST_YAML = "spring:\n  main:\n    banner-mode: off\n";
    private static final String PROD_YAML = "server:\n  forward-headers-strategy: framework\n";
    private static final String MIGRATION =
            """
            {{#starterSample}}
            CREATE TABLE greeting (
              id UUID PRIMARY KEY,
              message VARCHAR(255) NOT NULL,
              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            {{/starterSample}}
            {{^starterSample}}
            -- Backsmith baseline. Feature migrations begin at V2.
            SELECT 1;
            {{/starterSample}}
            """;
    private static final String GREETING_ENTITY =
            """
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
    private static final String GREETING_REPOSITORY =
            """
            package {{basePackage}}.modules.sample.repository;
            import {{basePackage}}.modules.sample.entity.Greeting;
            import java.util.UUID;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface GreetingRepository extends JpaRepository<Greeting, UUID> {}
            """;
    private static final String GREETING_SERVICE =
            """
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
    private static final String GREETING_CONTROLLER =
            """
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
    private static final String DOMAIN_GREETING =
            """
            package {{basePackage}}.modules.sample.domain.model;
            import java.util.Objects;
            import java.util.UUID;
            public record Greeting(UUID id, String message) {
                public Greeting { Objects.requireNonNull(id); if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank"); }
            }
            """;
    private static final String INPUT_PORT =
            """
            package {{basePackage}}.modules.sample.application.port.in;
            import {{basePackage}}.modules.sample.domain.model.Greeting;
            public interface GetGreetingUseCase { Greeting greeting(String message); }
            """;
    private static final String HEX_SERVICE =
            """
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
    private static final String HEX_CONTROLLER =
            """
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
    private static final String ERROR_HANDLER =
            """
            package {{basePackage}}.shared.error;

            import java.net.URI;
            import java.time.Instant;
            import java.util.*;
            {{#idempotency}}import {{basePackage}}.shared.idempotency.IdempotencyService.IdempotencyConflictException;{{/idempotency}}
            import org.slf4j.*;
            import org.springframework.dao.DataIntegrityViolationException;
            import org.springframework.http.*;
            {{#securityEnabled}}
            import org.springframework.security.access.AccessDeniedException;
            import org.springframework.security.authentication.BadCredentialsException;
            {{/securityEnabled}}
            import org.springframework.web.bind.MethodArgumentNotValidException;
            import org.springframework.web.bind.annotation.*;

            @RestControllerAdvice
            public class ApiExceptionHandler {
                private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

                @ExceptionHandler(MethodArgumentNotValidException.class)
                ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
                    List<Map<String, String>> fields = exception.getBindingResult().getFieldErrors().stream()
                            .map(error -> Map.of(
                                    "field", error.getField(),
                                    "message", Optional.ofNullable(error.getDefaultMessage()).orElse("invalid")))
                            .toList();
                    ProblemDetail detail = problem(
                            HttpStatus.BAD_REQUEST, "Validation failed", "validation", "VALIDATION_FAILED");
                    detail.setProperty("fieldErrors", fields);
                    return ResponseEntity.badRequest().body(detail);
                }

                {{#securityEnabled}}
                @ExceptionHandler(BadCredentialsException.class)
                ResponseEntity<ProblemDetail> authentication() {
                    return response(HttpStatus.UNAUTHORIZED, "Authentication failed", "authentication", "AUTHENTICATION_FAILED");
                }

                @ExceptionHandler(AccessDeniedException.class)
                ResponseEntity<ProblemDetail> authorization() {
                    return response(HttpStatus.FORBIDDEN, "Access denied", "authorization", "ACCESS_DENIED");
                }
                {{/securityEnabled}}

                @ExceptionHandler(NoSuchElementException.class)
                ResponseEntity<ProblemDetail> notFound() {
                    return response(HttpStatus.NOT_FOUND, "Resource not found", "not-found", "RESOURCE_NOT_FOUND");
                }

                @ExceptionHandler(DataIntegrityViolationException.class)
                ResponseEntity<ProblemDetail> conflict() {
                    return response(HttpStatus.CONFLICT, "The request conflicts with current state", "conflict", "DATA_CONFLICT");
                }

                {{#idempotency}}
                @ExceptionHandler(IdempotencyConflictException.class)
                ResponseEntity<ProblemDetail> idempotencyConflict() {
                    return response(HttpStatus.CONFLICT, "Idempotency key conflicts with the request",
                            "idempotency-conflict", "IDEMPOTENCY_CONFLICT");
                }
                {{/idempotency}}

                @ExceptionHandler(IllegalArgumentException.class)
                ResponseEntity<ProblemDetail> invalidArgument() {
                    return response(HttpStatus.BAD_REQUEST, "The request is invalid", "validation", "INVALID_ARGUMENT");
                }

                @ExceptionHandler(IllegalStateException.class)
                ResponseEntity<ProblemDetail> businessRule() {
                    return response(HttpStatus.CONFLICT, "A business rule rejected the request", "business-rule", "BUSINESS_RULE");
                }

                @ExceptionHandler(Exception.class)
                ResponseEntity<ProblemDetail> unexpected(Exception failure) {
                    LOG.error("unexpected_error type={} correlationId={}",
                            failure.getClass().getName(), MDC.get("correlationId"));
                    return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "internal", "INTERNAL_ERROR");
                }

                private ResponseEntity<ProblemDetail> response(
                        HttpStatus status, String title, String category, String code) {
                    return ResponseEntity.status(status).body(problem(status, title, category, code));
                }

                private ProblemDetail problem(
                        HttpStatus status, String title, String category, String code) {
                    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, title);
                    detail.setType(URI.create("https://errors.example.invalid/" + category));
                    detail.setTitle(title);
                    detail.setProperty("errorCode", code);
                    detail.setProperty("correlationId", Optional.ofNullable(MDC.get("correlationId")).orElse("unavailable"));
                    detail.setProperty("timestamp", Instant.now().toString());
                    return detail;
                }
            }
            """;
    private static final String CORRELATION_FILTER =
            """
            package {{basePackage}}.shared.web;
            import jakarta.servlet.*;
            import jakarta.servlet.http.*;
            import java.io.IOException;
            import java.util.UUID;
            import org.slf4j.MDC;
            import org.springframework.stereotype.Component;
            @Component
            public class CorrelationIdFilter extends org.springframework.web.filter.OncePerRequestFilter {
                private static final String HEADER = "X-Correlation-Id";
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    String id = request.getHeader(HEADER);
                    String correlationId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
                    response.setHeader(HEADER, correlationId);
                    try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
                        chain.doFilter(request, response);
                    }
                }
            }
            """;
    private static final String MODULAR_SERVICE =
            """
            package {{basePackage}}.modules.sample.application;

            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Service;

            @Service
            public class GreetingService {
                public Greeting create(String message) {
                    return new Greeting(UUID.randomUUID(), message);
                }
            }
            """;
    private static final String MODULAR_CONTROLLER =
            """
            package {{basePackage}}.modules.sample.api;

            import {{basePackage}}.modules.sample.application.GreetingService;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final GreetingService service;
                public GreetingController(GreetingService service) { this.service = service; }
                @GetMapping public Object greeting(@RequestParam String message) { return service.create(message); }
            }
            """;
    private static final String MODULAR_PACKAGE_INFO =
            """
            /**
             * Sample business module. Consumers must use the application or API packages;
             * domain and infrastructure packages are internal implementation details.
             */
            package {{basePackage}}.modules.sample;
            """;
    private static final String CLEAN_USE_CASE =
            """
            package {{basePackage}}.modules.sample.application;

            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Service;

            public interface GetGreeting {
                Greeting execute(String message);

                @Service
                final class Handler implements GetGreeting {
                    public Greeting execute(String message) { return new Greeting(UUID.randomUUID(), message); }
                }
            }
            """;
    private static final String CLEAN_CONTROLLER =
            """
            package {{basePackage}}.modules.sample.interfaceadapters.web;

            import {{basePackage}}.modules.sample.application.GetGreeting;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final GetGreeting useCase;
                public GreetingController(GetGreeting useCase) { this.useCase = useCase; }
                @GetMapping public Object greeting(@RequestParam String message) { return useCase.execute(message); }
            }
            """;
    private static final String ONION_SERVICE =
            """
            package {{basePackage}}.modules.sample.application;

            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Service;

            @Service
            public class GreetingApplicationService {
                public Greeting execute(String message) { return new Greeting(UUID.randomUUID(), message); }
            }
            """;
    private static final String ONION_CONTROLLER =
            """
            package {{basePackage}}.modules.sample.api;

            import {{basePackage}}.modules.sample.application.GreetingApplicationService;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final GreetingApplicationService service;
                public GreetingController(GreetingApplicationService service) { this.service = service; }
                @GetMapping public Object greeting(@RequestParam String message) { return service.execute(message); }
            }
            """;
    private static final String COMMAND_BUS =
            """
            package {{basePackage}}.modules.sample.application.command;

            public interface CommandBus {
                <R> R dispatch(Object command, Class<R> resultType);
            }
            """;
    private static final String QUERY_BUS =
            """
            package {{basePackage}}.modules.sample.application.query;

            public interface QueryBus {
                <R> R ask(Object query, Class<R> resultType);
            }
            """;
    private static final String GET_GREETING_QUERY =
            """
            package {{basePackage}}.modules.sample.application.query;

            public record GetGreetingQuery(String message) {
                public GetGreetingQuery {
                    if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
                }
            }
            """;
    private static final String CQRS_CONTROLLER =
            """
            package {{basePackage}}.modules.sample.api;

            import {{basePackage}}.modules.sample.application.command.CommandBus;
            import {{basePackage}}.modules.sample.application.command.CreateGreetingCommand;
            import {{basePackage}}.modules.sample.application.query.GetGreetingQuery;
            import {{basePackage}}.modules.sample.application.query.QueryBus;
            import {{basePackage}}.modules.sample.domain.Greeting;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/greetings")
            public class GreetingController {
                private final QueryBus queries;
                private final CommandBus commands;
                public GreetingController(QueryBus queries, CommandBus commands) {
                    this.queries = queries;
                    this.commands = commands;
                }
                @GetMapping
                public Greeting greeting(@RequestParam String message) {
                    return queries.ask(new GetGreetingQuery(message), Greeting.class);
                }
                @PostMapping
                public Greeting create(@RequestBody CreateGreetingCommand command) {
                    return commands.dispatch(command, Greeting.class);
                }
            }
            """;
    private static final String TIME_CONFIGURATION =
            """
            package {{basePackage}}.shared.configuration;

            import java.time.Clock;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class TimeConfiguration {
                @Bean
                Clock utcClock() {
                    return Clock.systemUTC();
                }
            }
            """;
    private static final String CREATE_GREETING_COMMAND =
            """
            package {{basePackage}}.modules.sample.application.command;

            public record CreateGreetingCommand(String message) {
                public CreateGreetingCommand {
                    if (message == null || message.isBlank()) {
                        throw new IllegalArgumentException("message is required");
                    }
                }
            }
            """;
    private static final String GREETING_PROJECTION =
            """
            package {{basePackage}}.modules.sample.application.query;

            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.Optional;
            import java.util.UUID;

            public interface GreetingProjection {
                Optional<Greeting> find(UUID id);
            }
            """;
    private static final String GREETING_CREATED_EVENT =
            """
            package {{basePackage}}.modules.sample.domain;

            import java.time.Instant;
            import java.util.UUID;

            public record GreetingCreated(UUID eventId, UUID greetingId, Instant occurredAt) {}
            """;
    private static final String SIMPLE_COMMAND_BUS =
            """
            package {{basePackage}}.modules.sample.infrastructure;

            import {{basePackage}}.modules.sample.application.command.CommandBus;
            import {{basePackage}}.modules.sample.application.command.CreateGreetingCommand;
            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Component;

            @Component
            public class SimpleCommandBus implements CommandBus {
                @Override
                public <R> R dispatch(Object command, Class<R> resultType) {
                    if (command instanceof CreateGreetingCommand create && resultType == Greeting.class) {
                        return resultType.cast(new Greeting(UUID.randomUUID(), create.message()));
                    }
                    throw new IllegalArgumentException("no command handler for " + command.getClass().getName());
                }
            }
            """;
    private static final String APPLICATION_PROPERTIES =
            """
            package {{basePackage}}.shared.configuration;

            import jakarta.validation.Valid;
            import jakarta.validation.constraints.NotNull;
            import java.time.Duration;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.validation.annotation.Validated;

            @Validated
            @ConfigurationProperties("backsmith")
            public record ApplicationProperties(@Valid Outbox outbox) {
                public ApplicationProperties {
                    outbox = outbox == null
                            ? new Outbox(Duration.ofSeconds(1), Duration.ofDays(7))
                            : outbox;
                }

                public record Outbox(@NotNull Duration delay, @NotNull Duration retention) {}
            }
            """;
    private static final String IDEMPOTENCY_KEY =
            """
            package {{basePackage}}.shared.web;

            public record IdempotencyKey(String value) {
                public IdempotencyKey {
                    if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
                        throw new IllegalArgumentException("invalid idempotency key");
                    }
                }
            }
            """;
    private static final String IDEMPOTENCY_SERVICE =
            """
            package {{basePackage}}.shared.idempotency;

            import {{basePackage}}.shared.web.IdempotencyKey;
            {{#multiTenancy}}import {{basePackage}}.shared.tenancy.TenantContext;{{/multiTenancy}}
            import jakarta.persistence.*;
            import java.time.*;
            import java.util.*;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.data.jpa.repository.Lock;
            import org.springframework.data.jpa.repository.Modifying;
            import org.springframework.data.jpa.repository.Query;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;

            @Service
            public class IdempotencyService {
                private final IdempotencyRepository repository;
                private final Clock clock;

                public IdempotencyService(IdempotencyRepository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                @Transactional
                public Claim begin(String rawKey, String requestHash) {
                    String key = new IdempotencyKey(rawKey).value();
                    if (requestHash == null || !requestHash.matches("[a-fA-F0-9]{64}")) {
                        throw new IllegalArgumentException("requestHash must be a SHA-256 hex value");
                    }
                    String tenantId = {{#multiTenancy}}TenantContext.requiredTenant(){{/multiTenancy}}{{^multiTenancy}}"global"{{/multiTenancy}};
                    Instant now = clock.instant();
                    int inserted = repository.insertIfAbsent(
                            UUID.randomUUID(),
                            tenantId,
                            key,
                            requestHash.toLowerCase(Locale.ROOT),
                            now,
                            now.plus(Duration.ofHours(24)));
                    IdempotencyRecord record =
                            repository.findByTenantIdAndKeyValue(tenantId, key)
                                    .orElseThrow(() -> new IllegalStateException("idempotency claim was not persisted"));
                    if (!record.requestHash.equalsIgnoreCase(requestHash)) {
                        throw new IdempotencyConflictException();
                    }
                    return record.claim(inserted == 1);
                }

                @Transactional
                public void complete(UUID claimId, int statusCode, String responseBody) {
                    IdempotencyRecord record =
                            repository.findById(claimId).orElseThrow(NoSuchElementException::new);
                    record.complete(statusCode, responseBody, clock.instant());
                }

                public record Claim(
                        UUID id,
                        boolean acquired,
                        boolean replay,
                        String state,
                        Integer statusCode,
                        String responseBody) {}

                public static final class IdempotencyConflictException extends IllegalStateException {
                    public IdempotencyConflictException() {
                        super("the idempotency key was already used for a different request");
                    }
                }
            }

            @Entity
            @Table(
                    name = "idempotency_record",
                    uniqueConstraints = @UniqueConstraint(
                            name = "uq_idempotency_tenant_key",
                            columnNames = {"tenant_id", "key_value"}))
            class IdempotencyRecord {
                @Id UUID id;
                @Column(name = "tenant_id", nullable = false, length = 64) String tenantId;
                @Column(name = "key_value", nullable = false, length = 128) String keyValue;
                @Column(name = "request_hash", nullable = false, length = 64) String requestHash;
                @Column(nullable = false, length = 16) String state;
                @Column(name = "status_code") Integer statusCode;
                @Column(name = "response_body", columnDefinition = "text") String responseBody;
                @Column(name = "created_at", nullable = false) Instant createdAt;
                @Column(name = "completed_at") Instant completedAt;
                @Column(name = "expires_at", nullable = false) Instant expiresAt;
                @Version long version;

                protected IdempotencyRecord() {}

                static IdempotencyRecord processing(
                        UUID id, String tenantId, String key, String requestHash, Instant now) {
                    var record = new IdempotencyRecord();
                    record.id = id;
                    record.tenantId = tenantId;
                    record.keyValue = key;
                    record.requestHash = requestHash;
                    record.state = "PROCESSING";
                    record.createdAt = now;
                    record.expiresAt = now.plus(Duration.ofHours(24));
                    return record;
                }

                void complete(int statusCode, String responseBody, Instant now) {
                    if (!state.equals("PROCESSING")) throw new IllegalStateException("claim is already complete");
                    this.statusCode = statusCode;
                    this.responseBody = responseBody;
                    this.completedAt = now;
                    this.state = "COMPLETED";
                }

                IdempotencyService.Claim claim(boolean acquired) {
                    return new IdempotencyService.Claim(
                            id, acquired, state.equals("COMPLETED"), state, statusCode, responseBody);
                }
            }

            interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
                @Modifying
                @Query(value = "INSERT INTO idempotency_record "
                        + "(id, tenant_id, key_value, request_hash, state, created_at, expires_at, version) "
                        + "VALUES (:id, :tenantId, :keyValue, :requestHash, 'PROCESSING', "
                        + ":createdAt, :expiresAt, 0) ON CONFLICT (tenant_id, key_value) DO NOTHING",
                        nativeQuery = true)
                int insertIfAbsent(
                        UUID id,
                        String tenantId,
                        String keyValue,
                        String requestHash,
                        Instant createdAt,
                        Instant expiresAt);

                @Lock(LockModeType.PESSIMISTIC_WRITE)
                Optional<IdempotencyRecord> findByTenantIdAndKeyValue(String tenantId, String key);
                long deleteByExpiresAtBefore(Instant before);
            }
            """;
    private static final String IDEMPOTENCY_CLEANUP =
            """
            package {{basePackage}}.shared.idempotency;

            import java.time.Clock;
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            @Component
            public class IdempotencyCleanup {
                private final IdempotencyRepository repository;
                private final Clock clock;

                public IdempotencyCleanup(IdempotencyRepository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                @Scheduled(cron = "${backsmith.idempotency.cleanup-cron:0 30 3 * * *}")
                @Transactional
                public void removeExpiredClaims() {
                    repository.deleteByExpiresAtBefore(clock.instant());
                }
            }
            """;
    private static final String IDEMPOTENCY_MIGRATION =
            """
            CREATE TABLE idempotency_record (
              id UUID PRIMARY KEY,
              tenant_id VARCHAR(64) NOT NULL,
              key_value VARCHAR(128) NOT NULL,
              request_hash VARCHAR(64) NOT NULL,
              state VARCHAR(16) NOT NULL,
              status_code INTEGER,
              response_body TEXT,
              created_at TIMESTAMPTZ NOT NULL,
              completed_at TIMESTAMPTZ,
              expires_at TIMESTAMPTZ NOT NULL,
              version BIGINT NOT NULL DEFAULT 0,
              CONSTRAINT uq_idempotency_tenant_key UNIQUE (tenant_id, key_value)
            );
            CREATE INDEX idx_idempotency_expiry ON idempotency_record(expires_at);
            """;
    private static final String AUDIT_EVENT =
            """
            package {{basePackage}}.shared.observability;

            import java.time.Instant;
            import java.util.Map;
            import java.util.UUID;

            public record AuditEvent(
                    UUID eventId,
                    String action,
                    String actorId,
                    String subjectId,
                    Instant occurredAt,
                    Map<String, String> safeMetadata) {
                public AuditEvent {
                    safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
                }
            }
            """;
    private static final String SECURITY_CONFIGURATION =
            """
            package {{basePackage}}.shared.security;

            import java.util.List;
            {{#localJwt}}
            import java.nio.charset.StandardCharsets;
            import javax.crypto.SecretKey;
            import javax.crypto.spec.SecretKeySpec;
            import com.nimbusds.jose.jwk.source.ImmutableSecret;
            import org.springframework.security.oauth2.jwt.JwtDecoder;
            import org.springframework.security.oauth2.jwt.JwtEncoder;
            import org.springframework.security.oauth2.jwt.JwtValidators;
            import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
            import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
            import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
            import org.springframework.security.oauth2.core.OAuth2Error;
            import org.springframework.security.oauth2.core.OAuth2TokenValidator;
            import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
            {{/localJwt}}
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
            import org.springframework.security.crypto.password.PasswordEncoder;
            import org.springframework.security.config.Customizer;
            import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.http.SessionCreationPolicy;
            {{#resourceServer}}import org.springframework.security.core.GrantedAuthority;{{/resourceServer}}
            {{#resourceServer}}import org.springframework.security.core.authority.SimpleGrantedAuthority;{{/resourceServer}}
            {{#resourceServer}}import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;{{/resourceServer}}
            import org.springframework.security.web.AuthenticationEntryPoint;
            import org.springframework.security.web.SecurityFilterChain;
            import org.springframework.security.web.access.AccessDeniedHandler;
            {{#sessionSecurity}}import org.springframework.security.web.csrf.CookieCsrfTokenRepository;{{/sessionSecurity}}
            import org.springframework.web.cors.CorsConfiguration;
            import org.springframework.web.cors.CorsConfigurationSource;
            import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

            @Configuration
            @EnableMethodSecurity
            public class SecurityConfiguration {
                @Bean
                SecurityFilterChain apiSecurity(
                        HttpSecurity http,
                        AuthenticationEntryPoint authenticationEntryPoint,
                        AccessDeniedHandler accessDeniedHandler) throws Exception {
                    http.cors(Customizer.withDefaults())
                        .exceptionHandling(errors -> errors
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                        .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                            .anyRequest().authenticated());
                    {{#resourceServer}}
                    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .csrf(csrf -> csrf.disable())
                        .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
                    {{/resourceServer}}
                    {{^resourceServer}}
                    {{#sessionSecurity}}
                    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                        .httpBasic(Customizer.withDefaults());
                    {{/sessionSecurity}}
                    {{^sessionSecurity}}
                    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .csrf(csrf -> csrf.disable())
                        .httpBasic(Customizer.withDefaults());
                    {{/sessionSecurity}}
                    {{/resourceServer}}
                    return http.build();
                }

                {{#resourceServer}}
                @Bean
                JwtAuthenticationConverter jwtAuthenticationConverter() {
                    var converter = new JwtAuthenticationConverter();
                    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        List<String> roles = jwt.getClaimAsStringList("roles");
                        if (roles == null) return List.of();
                        return roles.stream()
                                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                                .toList();
                    });
                    return converter;
                }
                {{/resourceServer}}

                @Bean
                AuthenticationEntryPoint authenticationEntryPoint() {
                    return (request, response, failure) -> {
                        response.setStatus(401);
                        response.setContentType("application/problem+json");
                        response.getWriter().write(
                                "{\\"type\\":\\"about:blank\\",\\"title\\":\\"Authentication required\\",\\"status\\":401}");
                    };
                }

                @Bean
                AccessDeniedHandler accessDeniedHandler() {
                    return (request, response, failure) -> {
                        response.setStatus(403);
                        response.setContentType("application/problem+json");
                        response.getWriter().write(
                                "{\\"type\\":\\"about:blank\\",\\"title\\":\\"Access denied\\",\\"status\\":403}");
                    };
                }

                @Bean
                PasswordEncoder passwordEncoder() {
                    return new BCryptPasswordEncoder(12);
                }

                {{#localJwt}}
                @Bean
                SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
                    if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                        throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
                    }
                    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                }

                @Bean
                JwtEncoder jwtEncoder(SecretKey key) {
                    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
                }

                @Bean
                JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.audience}") String audience) {
                    var decoder = NimbusJwtDecoder.withSecretKey(key).build();
                    OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator =
                            token -> token.getAudience().contains(audience)
                                    ? OAuth2TokenValidatorResult.success()
                                    : OAuth2TokenValidatorResult.failure(
                                            new OAuth2Error("invalid_token", "required audience is missing", null));
                    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer("{{artifactId}}"), audienceValidator));
                    return decoder;
                }
                {{/localJwt}}

                @Bean
                CorsConfigurationSource corsConfigurationSource(
                        @Value("${APP_ALLOWED_ORIGINS:http://localhost:3000}") List<String> origins) {
                    var configuration = new CorsConfiguration();
                    configuration.setAllowedOrigins(origins);
                    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
                    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
                    configuration.setAllowCredentials(true);
                    var source = new UrlBasedCorsConfigurationSource();
                    source.registerCorsConfiguration("/api/**", configuration);
                    return source;
                }
            }
            """;
    private static final String AUTH_ACCOUNT =
            """
            package {{basePackage}}.modules.authentication.domain;

            import java.time.Instant;
            import java.util.Set;
            import java.util.UUID;

            public record Account(
                    UUID id,
                    String email,
                    String passwordHash,
                    Set<Role> roles,
                    boolean enabled,
                    Instant createdAt) {
                public Account {
                    roles = roles == null ? Set.of() : Set.copyOf(roles);
                    if (email == null || !email.contains("@")) throw new IllegalArgumentException("invalid email");
                    if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("password hash is required");
                }
            }
            """;
    private static final String AUTH_ROLE =
            """
            package {{basePackage}}.modules.authentication.domain;

            public enum Role {
                USER,
                ADMIN
            }
            """;
    private static final String AUTH_SERVICE =
            """
            package {{basePackage}}.modules.authentication.application;

            import java.util.Optional;
            import java.util.Set;

            public interface AuthenticationService {
                CurrentAccount register(String email, char[] password, String tenantId);
                TokenPair login(String email, char[] password);
                TokenPair refresh(String refreshToken);
                void logout(String refreshToken);
                Optional<CurrentAccount> currentAccount();

                record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
                record CurrentAccount(String id, String email, Set<String> tenants) {}
            }
            """;
    private static final String SIMPLE_QUERY_BUS =
            """
            package {{basePackage}}.modules.sample.infrastructure;

            import {{basePackage}}.modules.sample.application.query.GetGreetingQuery;
            import {{basePackage}}.modules.sample.application.query.QueryBus;
            import {{basePackage}}.modules.sample.domain.Greeting;
            import java.util.UUID;
            import org.springframework.stereotype.Component;

            @Component
            public class SimpleQueryBus implements QueryBus {
                @Override
                public <R> R ask(Object query, Class<R> resultType) {
                    if (query instanceof GetGreetingQuery greeting && resultType == Greeting.class) {
                        return resultType.cast(new Greeting(UUID.randomUUID(), greeting.message()));
                    }
                    throw new IllegalArgumentException("no query handler for " + query.getClass().getName());
                }
            }
            """;
    private static final String AUTH_JPA_SERVICE =
            """
            package {{basePackage}}.modules.authentication.infrastructure;

            import {{basePackage}}.modules.authentication.application.AuthenticationService;
            import {{basePackage}}.modules.authentication.domain.Role;
            import jakarta.persistence.*;
            import java.nio.charset.StandardCharsets;
            import java.security.MessageDigest;
            import java.security.SecureRandom;
            import java.time.*;
            import java.util.*;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.security.authentication.BadCredentialsException;
            import org.springframework.security.core.context.SecurityContextHolder;
            import org.springframework.security.crypto.password.PasswordEncoder;
            import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
            import org.springframework.security.oauth2.jwt.*;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;

            @Service
            @Transactional
            public class JpaAuthenticationService implements AuthenticationService {
                private static final SecureRandom RANDOM = new SecureRandom();
                private final AccountRepository accounts;
                private final RefreshTokenRepository refreshTokens;
                private final PasswordEncoder passwords;
                private final JwtEncoder jwtEncoder;
                private final Clock clock;
                private final String audience;
                private final Duration accessTtl;
                private final Duration refreshTtl;

                public JpaAuthenticationService(
                        AccountRepository accounts,
                        RefreshTokenRepository refreshTokens,
                        PasswordEncoder passwords,
                        JwtEncoder jwtEncoder,
                        Clock clock,
                        @Value("${app.jwt.audience}") String audience,
                        @Value("${app.jwt.access-token-ttl}") Duration accessTtl,
                        @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl) {
                    this.accounts = accounts;
                    this.refreshTokens = refreshTokens;
                    this.passwords = passwords;
                    this.jwtEncoder = jwtEncoder;
                    this.clock = clock;
                    this.audience = audience;
                    this.accessTtl = accessTtl;
                    this.refreshTtl = refreshTtl;
                }

                @Override
                public CurrentAccount register(String email, char[] password, String tenantId) {
                    String normalized = normalizeEmail(email);
                    if (accounts.existsByEmailIgnoreCase(normalized)) {
                        throw new IllegalArgumentException("account email already exists");
                    }
                    requireStrongPassword(password);
                    var account = new AccountEntity();
                    account.id = UUID.randomUUID();
                    account.email = normalized;
                    account.passwordHash = passwords.encode(new String(password));
                    account.enabled = true;
                    account.roles = Set.of(Role.USER);
                    account.tenants =
                            Set.of(tenantId == null || tenantId.isBlank() ? "default" : tenantId);
                    account.createdAt = clock.instant();
                    account.updatedAt = account.createdAt;
                    accounts.save(account);
                    return current(account);
                }

                @Override
                public TokenPair login(String email, char[] password) {
                    AccountEntity account = accounts.findByEmailIgnoreCase(normalizeEmail(email))
                            .filter(candidate -> candidate.enabled)
                            .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
                    if (!passwords.matches(new String(password), account.passwordHash)) {
                        throw new BadCredentialsException("invalid credentials");
                    }
                    return issue(account, UUID.randomUUID());
                }

                @Override
                public TokenPair refresh(String refreshToken) {
                    Instant now = clock.instant();
                    RefreshTokenEntity existing = refreshTokens.findByTokenHash(hash(refreshToken))
                            .orElseThrow(() -> new BadCredentialsException("invalid refresh token"));
                    if (existing.revokedAt != null) {
                        refreshTokens.findAllByFamilyIdAndRevokedAtIsNull(existing.familyId)
                                .forEach(token -> token.revokedAt = now);
                        throw new BadCredentialsException("invalid refresh token");
                    }
                    if (!existing.expiresAt.isAfter(now)) {
                        throw new BadCredentialsException("invalid refresh token");
                    }
                    existing.revokedAt = now;
                    AccountEntity account = accounts.findById(existing.accountId)
                            .filter(candidate -> candidate.enabled)
                            .orElseThrow(() -> new BadCredentialsException("account is disabled"));
                    return issue(account, existing.familyId);
                }

                @Override
                public void logout(String refreshToken) {
                    refreshTokens.findByTokenHash(hash(refreshToken))
                            .filter(token -> token.revokedAt == null)
                            .ifPresent(token -> token.revokedAt = clock.instant());
                }

                @Override
                @Transactional(readOnly = true)
                public Optional<CurrentAccount> currentAccount() {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
                    try {
                        return accounts.findById(UUID.fromString(authentication.getName())).map(this::current);
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                }

                private TokenPair issue(AccountEntity account, UUID familyId) {
                    Instant now = clock.instant();
                    Instant expiresAt = now.plus(accessTtl);
                    var claims = JwtClaimsSet.builder()
                            .issuer("{{artifactId}}")
                            .subject(account.id.toString())
                            .audience(List.of(audience))
                            .issuedAt(now)
                            .expiresAt(expiresAt)
                            .claim("email", account.email)
                            .claim("roles", account.roles.stream().map(Enum::name).sorted().toList())
                            .claim("tenants", account.tenants.stream().sorted().toList())
                            .build();
                    String accessToken = jwtEncoder.encode(
                            JwtEncoderParameters.from(
                                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
                    byte[] random = new byte[48];
                    RANDOM.nextBytes(random);
                    String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                    var stored = new RefreshTokenEntity();
                    stored.id = UUID.randomUUID();
                    stored.accountId = account.id;
                    stored.tokenHash = hash(rawRefresh);
                    stored.familyId = familyId;
                    stored.expiresAt = now.plus(refreshTtl);
                    stored.createdAt = now;
                    refreshTokens.save(stored);
                    return new TokenPair(accessToken, rawRefresh, accessTtl.toSeconds());
                }

                private CurrentAccount current(AccountEntity account) {
                    return new CurrentAccount(account.id.toString(), account.email, account.tenants);
                }

                private String normalizeEmail(String email) {
                    if (email == null || !email.contains("@")) throw new IllegalArgumentException("invalid email");
                    return email.trim().toLowerCase(Locale.ROOT);
                }

                private void requireStrongPassword(char[] password) {
                    if (password == null || password.length < 12) {
                        throw new IllegalArgumentException("password must contain at least 12 characters");
                    }
                }

                private String hash(String token) {
                    if (token == null || token.isBlank()) throw new BadCredentialsException("invalid refresh token");
                    try {
                        return HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(token.getBytes(StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException impossible) {
                        throw new IllegalStateException(impossible);
                    }
                }
            }

            @Entity
            @Table(name = "account")
            class AccountEntity {
                @Id UUID id;
                @Column(nullable = false, unique = true, length = 320) String email;
                @Column(name = "password_hash", nullable = false) String passwordHash;
                @Column(nullable = false) boolean enabled;
                @ElementCollection(fetch = FetchType.EAGER)
                @CollectionTable(name = "account_role", joinColumns = @JoinColumn(name = "account_id"))
                @Enumerated(EnumType.STRING)
                @Column(name = "role", nullable = false)
                Set<Role> roles = Set.of();
                @ElementCollection(fetch = FetchType.EAGER)
                @CollectionTable(name = "account_tenant", joinColumns = @JoinColumn(name = "account_id"))
                @Column(name = "tenant_id", nullable = false, length = 64)
                Set<String> tenants = Set.of();
                @Column(name = "created_at", nullable = false) Instant createdAt;
                @Column(name = "updated_at", nullable = false) Instant updatedAt;
                @Version long version;
            }

            @Entity
            @Table(name = "refresh_token")
            class RefreshTokenEntity {
                @Id UUID id;
                @Column(name = "account_id", nullable = false) UUID accountId;
                @Column(name = "token_hash", nullable = false, unique = true, length = 128) String tokenHash;
                @Column(name = "family_id", nullable = false) UUID familyId;
                @Column(name = "expires_at", nullable = false) Instant expiresAt;
                @Column(name = "revoked_at") Instant revokedAt;
                @Column(name = "replaced_by") UUID replacedBy;
                @Column(name = "created_at", nullable = false) Instant createdAt;
            }

            interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
                Optional<AccountEntity> findByEmailIgnoreCase(String email);
                boolean existsByEmailIgnoreCase(String email);
            }

            interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
                Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
                List<RefreshTokenEntity> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);
            }
            """;
    private static final String AUTH_CONTROLLER =
            """
            package {{basePackage}}.modules.authentication.api;

            import {{basePackage}}.modules.authentication.application.AuthenticationService;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.Email;
            import jakarta.validation.constraints.NotBlank;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/auth")
            public class AuthenticationController {
                private final AuthenticationService authentication;

                public AuthenticationController(AuthenticationService authentication) {
                    this.authentication = authentication;
                }

                @PostMapping("/register")
                ResponseEntity<AuthenticationService.CurrentAccount> register(
                        @Valid @RequestBody RegistrationRequest request) {
                    char[] password = request.password().toCharArray();
                    try {
                        return ResponseEntity.status(201)
                                .body(authentication.register(request.email(), password, request.tenantId()));
                    } finally {
                        java.util.Arrays.fill(password, '\\0');
                    }
                }

                @PostMapping("/login")
                ResponseEntity<AuthenticationService.TokenPair> login(@Valid @RequestBody LoginRequest request) {
                    char[] password = request.password().toCharArray();
                    try {
                        return ResponseEntity.ok(authentication.login(request.email(), password));
                    } finally {
                        java.util.Arrays.fill(password, '\\0');
                    }
                }

                @PostMapping("/refresh")
                AuthenticationService.TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
                    return authentication.refresh(request.refreshToken());
                }

                @PostMapping("/logout")
                ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
                    authentication.logout(request.refreshToken());
                    return ResponseEntity.noContent().build();
                }

                @GetMapping("/me")
                ResponseEntity<AuthenticationService.CurrentAccount> me() {
                    return authentication.currentAccount()
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build());
                }

                record LoginRequest(@Email String email, @NotBlank String password) {}
                record RegistrationRequest(
                        @Email String email,
                        @NotBlank String password,
                        {{#multiTenancy}}@NotBlank{{/multiTenancy}}
                        @jakarta.validation.constraints.Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String tenantId) {}
                record RefreshRequest(@NotBlank String refreshToken) {}
            }
            """;
    private static final String AUTH_MIGRATION =
            """
            CREATE TABLE account (
              id UUID PRIMARY KEY,
              email VARCHAR(320) NOT NULL UNIQUE,
              password_hash VARCHAR(255) NOT NULL,
              enabled BOOLEAN NOT NULL DEFAULT TRUE,
              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
              version BIGINT NOT NULL DEFAULT 0
            );
            CREATE TABLE account_role (
              account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
              role VARCHAR(64) NOT NULL,
              PRIMARY KEY (account_id, role)
            );
            CREATE TABLE account_tenant (
              account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
              tenant_id VARCHAR(64) NOT NULL,
              PRIMARY KEY (account_id, tenant_id)
            );
            CREATE INDEX idx_account_tenant_tenant ON account_tenant(tenant_id, account_id);
            CREATE TABLE refresh_token (
              id UUID PRIMARY KEY,
              account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
              token_hash VARCHAR(128) NOT NULL UNIQUE,
              family_id UUID NOT NULL,
              expires_at TIMESTAMPTZ NOT NULL,
              revoked_at TIMESTAMPTZ,
              replaced_by UUID,
              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX idx_refresh_token_account ON refresh_token(account_id);
            """;
    private static final String CUSTOMER =
            """
            package {{basePackage}}.modules.customer.domain;

            import java.time.Instant;
            import java.util.UUID;

            public record Customer(UUID id, String name, String email, Instant createdAt) {
                public Customer {
                    if (id == null) throw new IllegalArgumentException("id is required");
                    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
                    if (email == null || !email.contains("@")) throw new IllegalArgumentException("email is invalid");
                }
            }
            """;
    private static final String CUSTOMER_SERVICE =
            """
            package {{basePackage}}.modules.customer.application;

            import {{basePackage}}.modules.customer.domain.Customer;
            {{#multiTenancy}}import {{basePackage}}.shared.tenancy.TenantContext;{{/multiTenancy}}
            import jakarta.persistence.*;
            import java.time.Clock;
            import java.time.Instant;
            import java.util.*;
            import org.springframework.data.domain.*;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;

            @Service
            public class CustomerService {
                private final CustomerJpaRepository repository;
                private final Clock clock;

                public CustomerService(CustomerJpaRepository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                @Transactional
                public Customer create(String name, String email) {
                    {{#multiTenancy}}
                    String tenantId = TenantContext.requiredTenant();
                    if (repository.existsByEmailIgnoreCaseAndTenantId(email, tenantId)) {
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    if (repository.existsByEmailIgnoreCase(email)) {
                    {{/multiTenancy}}
                        throw new IllegalArgumentException("customer email already exists");
                    }
                    return toDomain(repository.save(new CustomerRow(
                            UUID.randomUUID(), name, email, clock.instant()
                            {{#multiTenancy}}, tenantId{{/multiTenancy}})));
                }

                @Transactional(readOnly = true)
                public Optional<Customer> find(UUID id) {
                    {{#multiTenancy}}
                    return repository.findByIdAndTenantId(id, TenantContext.requiredTenant()).map(this::toDomain);
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    return repository.findById(id).map(this::toDomain);
                    {{/multiTenancy}}
                }

                @Transactional
                public Customer update(UUID id, String name, String email) {
                    {{#multiTenancy}}
                    CustomerRow row = repository.findByIdAndTenantId(id, TenantContext.requiredTenant())
                            .orElseThrow(NoSuchElementException::new);
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    CustomerRow row = repository.findById(id).orElseThrow(NoSuchElementException::new);
                    {{/multiTenancy}}
                    row.update(name, email);
                    return toDomain(row);
                }

                @Transactional(readOnly = true)
                public Page<Customer> search(String query, Pageable pageable) {
                    {{#multiTenancy}}
                    return repository.findByNameContainingIgnoreCaseAndTenantId(
                                    query == null ? "" : query, TenantContext.requiredTenant(), pageable)
                            .map(this::toDomain);
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    return repository.findByNameContainingIgnoreCase(query == null ? "" : query, pageable)
                            .map(this::toDomain);
                    {{/multiTenancy}}
                }

                private Customer toDomain(CustomerRow row) {
                    return new Customer(row.id(), row.name(), row.email(), row.createdAt());
                }
            }

            @Entity
            @Table(name = "customer")
            class CustomerRow {
                @Id private UUID id;
                private String name;
                private String email;
                private Instant createdAt;
                {{#multiTenancy}}private String tenantId;{{/multiTenancy}}
                @Version private long version;
                protected CustomerRow() {}
                CustomerRow(UUID id, String name, String email, Instant createdAt
                        {{#multiTenancy}}, String tenantId{{/multiTenancy}}) {
                    this.id = id; this.name = name; this.email = email; this.createdAt = createdAt;
                    {{#multiTenancy}}this.tenantId = tenantId;{{/multiTenancy}}
                }
                void update(String name, String email) { this.name = name; this.email = email; }
                UUID id() { return id; }
                String name() { return name; }
                String email() { return email; }
                Instant createdAt() { return createdAt; }
            }

            interface CustomerJpaRepository extends JpaRepository<CustomerRow, UUID> {
                {{#multiTenancy}}
                boolean existsByEmailIgnoreCaseAndTenantId(String email, String tenantId);
                Optional<CustomerRow> findByIdAndTenantId(UUID id, String tenantId);
                Page<CustomerRow> findByNameContainingIgnoreCaseAndTenantId(
                        String name, String tenantId, Pageable pageable);
                {{/multiTenancy}}
                {{^multiTenancy}}
                boolean existsByEmailIgnoreCase(String email);
                Page<CustomerRow> findByNameContainingIgnoreCase(String name, Pageable pageable);
                {{/multiTenancy}}
            }
            """;
    private static final String CUSTOMER_CONTROLLER =
            """
            package {{basePackage}}.modules.customer.api;

            import {{basePackage}}.modules.customer.application.CustomerService;
            import {{basePackage}}.modules.customer.domain.Customer;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.*;
            import java.util.UUID;
            import org.springframework.data.domain.*;
            import org.springframework.data.web.PageableDefault;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/customers")
            public class CustomerController {
                private final CustomerService customers;
                public CustomerController(CustomerService customers) { this.customers = customers; }

                @PostMapping
                ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(customers.create(request.name(), request.email()));
                }

                @GetMapping("/{id}")
                ResponseEntity<Customer> find(@PathVariable UUID id) {
                    return customers.find(id).map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build());
                }

                @PutMapping("/{id}")
                Customer update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
                    return customers.update(id, request.name(), request.email());
                }

                @GetMapping
                Page<Customer> search(
                        @RequestParam(defaultValue = "") String query,
                        @PageableDefault(size = 20) Pageable pageable) {
                    return customers.search(query, pageable);
                }

                record CustomerRequest(@NotBlank @Size(max = 120) String name, @Email String email) {}
            }
            """;
    private static final String CUSTOMER_MIGRATION =
            """
            CREATE TABLE customer (
              id UUID PRIMARY KEY,
              name VARCHAR(120) NOT NULL,
              email VARCHAR(320) NOT NULL{{^multiTenancy}} UNIQUE{{/multiTenancy}},
              created_at TIMESTAMPTZ NOT NULL,
              {{#multiTenancy}}tenant_id VARCHAR(64) NOT NULL,{{/multiTenancy}}
              version BIGINT NOT NULL DEFAULT 0
            );
            CREATE INDEX idx_customer_name ON customer(name);
            {{#multiTenancy}}
            ALTER TABLE customer ADD CONSTRAINT uq_customer_tenant_email UNIQUE (tenant_id, email);
            CREATE INDEX idx_customer_tenant_name ON customer(tenant_id, name);
            {{/multiTenancy}}
            """;
    private static final String PAYMENT_MONEY =
            """
            package {{basePackage}}.modules.payment.domain;

            import java.math.BigDecimal;
            import java.util.Currency;
            import java.util.Objects;

            public record Money(BigDecimal amount, Currency currency) {
                public Money {
                    Objects.requireNonNull(amount, "amount");
                    Objects.requireNonNull(currency, "currency");
                    if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
                }
            }
            """;
    private static final String PAYMENT_STATUS =
            """
            package {{basePackage}}.modules.payment.domain;

            public enum PaymentStatus {
                CREATED,
                VALIDATING,
                AUTHORIZED,
                PROCESSING,
                COMPLETED,
                REJECTED,
                FAILED
            }
            """;
    private static final String PAYMENT =
            """
            package {{basePackage}}.modules.payment.domain;

            import java.time.Instant;
            import java.util.UUID;

            public final class Payment {
                private final UUID id;
                private final String payerReference;
                private final String payeeReference;
                private final Money money;
                private final String idempotencyKey;
                private final Instant createdAt;
                private PaymentStatus status;

                public Payment(UUID id, String payerReference, String payeeReference, Money money,
                        String idempotencyKey, Instant createdAt, PaymentStatus status) {
                    this.id = java.util.Objects.requireNonNull(id);
                    this.payerReference = require(payerReference, "payerReference");
                    this.payeeReference = require(payeeReference, "payeeReference");
                    this.money = java.util.Objects.requireNonNull(money);
                    this.idempotencyKey = require(idempotencyKey, "idempotencyKey");
                    this.createdAt = java.util.Objects.requireNonNull(createdAt);
                    this.status = java.util.Objects.requireNonNull(status);
                }

                public void transitionTo(PaymentStatus next) {
                    boolean allowed = switch (status) {
                        case CREATED -> next == PaymentStatus.VALIDATING || next == PaymentStatus.REJECTED;
                        case VALIDATING -> next == PaymentStatus.AUTHORIZED || next == PaymentStatus.REJECTED;
                        case AUTHORIZED -> next == PaymentStatus.PROCESSING || next == PaymentStatus.FAILED;
                        case PROCESSING -> next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED;
                        case COMPLETED, REJECTED, FAILED -> false;
                    };
                    if (!allowed) throw new IllegalStateException("invalid payment transition: " + status + " -> " + next);
                    status = next;
                }

                private static String require(String value, String field) {
                    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
                    return value;
                }
                public UUID id() { return id; }
                public String payerReference() { return payerReference; }
                public String payeeReference() { return payeeReference; }
                public Money money() { return money; }
                public String idempotencyKey() { return idempotencyKey; }
                public Instant createdAt() { return createdAt; }
                public PaymentStatus status() { return status; }
            }
            """;
    private static final String PAYMENT_SERVICE =
            """
            package {{basePackage}}.modules.payment.application;

            import {{basePackage}}.modules.payment.domain.*;
            import {{basePackage}}.shared.observability.AuditEvent;
            import {{basePackage}}.shared.observability.AuditLogger;
            {{#outbox}}import {{basePackage}}.shared.messaging.outbox.TransactionalOutbox;{{/outbox}}
            {{#multiTenancy}}import {{basePackage}}.shared.tenancy.TenantContext;{{/multiTenancy}}
            import jakarta.persistence.*;
            import java.math.BigDecimal;
            import java.time.*;
            import java.util.*;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;

            @Service
            public class PaymentService {
                private final PaymentJpaRepository repository;
                private final Clock clock;
                private final AuditLogger audit;
                {{#outbox}}private final TransactionalOutbox outbox;{{/outbox}}
                public PaymentService(
                        PaymentJpaRepository repository,
                        Clock clock,
                        AuditLogger audit{{#outbox}},
                        TransactionalOutbox outbox{{/outbox}}) {
                    this.repository = repository;
                    this.clock = clock;
                    this.audit = audit;
                    {{#outbox}}this.outbox = outbox;{{/outbox}}
                }

                @Transactional
                public Payment initiate(String payer, String payee, BigDecimal amount, Currency currency, String key) {
                    {{#multiTenancy}}
                    String tenantId = TenantContext.requiredTenant();
                    return repository.findByIdempotencyKeyAndTenantId(key, tenantId).map(this::toDomain).orElseGet(() -> {
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    return repository.findByIdempotencyKey(key).map(this::toDomain).orElseGet(() -> {
                    {{/multiTenancy}}
                        Payment payment = new Payment(UUID.randomUUID(), payer, payee, new Money(amount, currency),
                                key, clock.instant(), PaymentStatus.CREATED);
                        Payment stored = toDomain(repository.save(PaymentRow.from(
                                payment{{#multiTenancy}}, tenantId{{/multiTenancy}})));
                        audit.record(new AuditEvent(
                                UUID.randomUUID(), "payment.initiated", null,
                                stored.id().toString(), clock.instant(), Map.of("status", stored.status().name())));
                        {{#outbox}}
                        outbox.append(stored.id().toString(), "payment.created.v1",
                                "{\\"paymentId\\":\\"" + stored.id() + "\\",\\"status\\":\\"CREATED\\"}");
                        {{/outbox}}
                        return stored;
                    });
                }

                @Transactional
                public Payment transition(UUID id, PaymentStatus next) {
                    {{#multiTenancy}}
                    PaymentRow row = repository.findByIdAndTenantId(id, TenantContext.requiredTenant())
                            .orElseThrow(NoSuchElementException::new);
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    PaymentRow row = repository.findById(id).orElseThrow(NoSuchElementException::new);
                    {{/multiTenancy}}
                    Payment payment = toDomain(row);
                    payment.transitionTo(next);
                    row.status(payment.status());
                    audit.record(new AuditEvent(
                            UUID.randomUUID(), "payment.transitioned", null,
                            payment.id().toString(), clock.instant(), Map.of("status", next.name())));
                    {{#outbox}}
                    outbox.append(payment.id().toString(), "payment.status-changed.v1",
                            "{\\"paymentId\\":\\"" + payment.id() + "\\",\\"status\\":\\"" + next + "\\"}");
                    {{/outbox}}
                    return payment;
                }

                @Transactional(readOnly = true)
                public Optional<Payment> find(UUID id) {
                    {{#multiTenancy}}
                    return repository.findByIdAndTenantId(id, TenantContext.requiredTenant()).map(this::toDomain);
                    {{/multiTenancy}}
                    {{^multiTenancy}}
                    return repository.findById(id).map(this::toDomain);
                    {{/multiTenancy}}
                }

                private Payment toDomain(PaymentRow row) {
                    return new Payment(row.id(), row.payerReference(), row.payeeReference(),
                            new Money(row.amount(), Currency.getInstance(row.currency())), row.idempotencyKey(),
                            row.createdAt(), row.status());
                }
            }

            @Entity
            @Table(name = "payment")
            class PaymentRow {
                @Id private UUID id;
                private String payerReference;
                private String payeeReference;
                private BigDecimal amount;
                private String currency;
                @Enumerated(EnumType.STRING) private PaymentStatus status;
                private String idempotencyKey;
                private Instant createdAt;
                {{#multiTenancy}}private String tenantId;{{/multiTenancy}}
                @Version private long version;
                protected PaymentRow() {}
                static PaymentRow from(Payment payment{{#multiTenancy}}, String tenantId{{/multiTenancy}}) {
                    var row = new PaymentRow();
                    row.id = payment.id(); row.payerReference = payment.payerReference();
                    row.payeeReference = payment.payeeReference(); row.amount = payment.money().amount();
                    row.currency = payment.money().currency().getCurrencyCode(); row.status = payment.status();
                    row.idempotencyKey = payment.idempotencyKey(); row.createdAt = payment.createdAt();
                    {{#multiTenancy}}row.tenantId = tenantId;{{/multiTenancy}}
                    return row;
                }
                void status(PaymentStatus value) { status = value; }
                UUID id() { return id; }
                String payerReference() { return payerReference; }
                String payeeReference() { return payeeReference; }
                BigDecimal amount() { return amount; }
                String currency() { return currency; }
                PaymentStatus status() { return status; }
                String idempotencyKey() { return idempotencyKey; }
                Instant createdAt() { return createdAt; }
            }

            interface PaymentJpaRepository extends JpaRepository<PaymentRow, UUID> {
                {{#multiTenancy}}
                Optional<PaymentRow> findByIdempotencyKeyAndTenantId(String idempotencyKey, String tenantId);
                Optional<PaymentRow> findByIdAndTenantId(UUID id, String tenantId);
                {{/multiTenancy}}
                {{^multiTenancy}}
                Optional<PaymentRow> findByIdempotencyKey(String idempotencyKey);
                {{/multiTenancy}}
            }
            """;
    private static final String PAYMENT_CONTROLLER =
            """
            package {{basePackage}}.modules.payment.api;

            import {{basePackage}}.modules.payment.application.PaymentService;
            import {{basePackage}}.modules.payment.domain.*;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.*;
            import java.math.BigDecimal;
            import java.util.Currency;
            import java.util.UUID;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/payments")
            public class PaymentController {
                private final PaymentService payments;
                public PaymentController(PaymentService payments) { this.payments = payments; }

                @PostMapping
                ResponseEntity<PaymentResponse> initiate(
                        @RequestHeader("Idempotency-Key") String key,
                        @Valid @RequestBody PaymentRequest request) {
                    Payment payment = payments.initiate(request.payerReference(), request.payeeReference(),
                            request.amount(), Currency.getInstance(request.currency()), key);
                    return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
                }

                @PostMapping("/{id}/transitions/{status}")
                PaymentResponse transition(@PathVariable UUID id, @PathVariable PaymentStatus status) {
                    return PaymentResponse.from(payments.transition(id, status));
                }

                record PaymentRequest(@NotBlank String payerReference, @NotBlank String payeeReference,
                        @DecimalMin("0.01") BigDecimal amount, @Pattern(regexp = "[A-Z]{3}") String currency) {}
                record PaymentResponse(UUID id, BigDecimal amount, String currency, PaymentStatus status) {
                    static PaymentResponse from(Payment payment) {
                        return new PaymentResponse(payment.id(), payment.money().amount(),
                                payment.money().currency().getCurrencyCode(), payment.status());
                    }
                }
            }
            """;
    private static final String PAYMENT_MIGRATION =
            """
            CREATE TABLE payment (
              id UUID PRIMARY KEY,
              payer_reference VARCHAR(255) NOT NULL,
              payee_reference VARCHAR(255) NOT NULL,
              amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
              currency CHAR(3) NOT NULL,
              status VARCHAR(32) NOT NULL,
              idempotency_key VARCHAR(128) NOT NULL{{^multiTenancy}} UNIQUE{{/multiTenancy}},
              created_at TIMESTAMPTZ NOT NULL,
              {{#multiTenancy}}tenant_id VARCHAR(64) NOT NULL,{{/multiTenancy}}
              version BIGINT NOT NULL DEFAULT 0
            );
            CREATE INDEX idx_payment_status ON payment(status);
            {{#multiTenancy}}
            ALTER TABLE payment ADD CONSTRAINT uq_payment_tenant_idempotency
              UNIQUE (tenant_id, idempotency_key);
            CREATE INDEX idx_payment_tenant_status ON payment(tenant_id, status);
            {{/multiTenancy}}
            """;
    private static final String PAYMENT_TEST =
            """
            package {{basePackage}}.modules.payment.domain;

            import static org.junit.jupiter.api.Assertions.assertThrows;
            import java.math.BigDecimal;
            import java.time.Instant;
            import java.util.Currency;
            import java.util.UUID;
            import org.junit.jupiter.api.Test;

            class PaymentTest {
                @Test
                void rejectsInvalidStateTransitions() {
                    var payment = new Payment(UUID.randomUUID(), "payer", "payee",
                            new Money(new BigDecimal("10.00"), Currency.getInstance("USD")),
                            "idempotency-key", Instant.now(), PaymentStatus.CREATED);
                    assertThrows(IllegalStateException.class, () -> payment.transitionTo(PaymentStatus.COMPLETED));
                }
            }
            """;
    private static final String EVENT_ENVELOPE =
            """
            package {{basePackage}}.shared.messaging;

            import java.time.Instant;
            import java.util.UUID;

            public record EventEnvelope<T>(
                    UUID eventId,
                    String eventType,
                    int eventVersion,
                    String aggregateId,
                    String tenantId,
                    String correlationId,
                    String causationId,
                    Instant occurredAt,
                    T payload) {}
            """;
    private static final String KAFKA_EVENT_PUBLISHER =
            """
            package {{basePackage}}.shared.messaging;

            import java.util.concurrent.CompletionStage;
            import org.springframework.kafka.core.KafkaTemplate;
            import org.springframework.stereotype.Component;

            @Component
            public class KafkaEventPublisher {
                private final KafkaTemplate<String, Object> template;

                public KafkaEventPublisher(KafkaTemplate<String, Object> template) {
                    this.template = template;
                }

                public CompletionStage<Void> publish(String topic, String key, EventEnvelope<?> event) {
                    return template.send(topic, key, event).thenApply(result -> null);
                }
            }
            """;
    private static final String IDEMPOTENT_EVENT_STORE =
            """
            package {{basePackage}}.shared.messaging;

            import java.util.UUID;

            public interface IdempotentEventStore {
                boolean claim(UUID eventId, String consumer);
                void complete(UUID eventId, String consumer);
                void release(UUID eventId, String consumer, String sanitizedError);
            }
            """;
    private static final String OUTBOX_EVENT =
            """
            package {{basePackage}}.shared.messaging.outbox;

            import jakarta.persistence.*;
            import java.time.Instant;
            import java.util.UUID;

            @Entity
            @Table(name = "outbox_event")
            public class OutboxEvent {
                @Id private UUID id;
                private String aggregateId;
                private String eventType;
                @Column(columnDefinition = "jsonb") private String payload;
                private String status;
                private int attempts;
                private Instant occurredAt;
                private Instant nextAttemptAt;
                private Instant publishedAt;
                private String error;
                {{#multiTenancy}}private String tenantId;{{/multiTenancy}}

                protected OutboxEvent() {}

                public static OutboxEvent pending(String aggregateId, String eventType, String payload,
                        Instant occurredAt{{#multiTenancy}}, String tenantId{{/multiTenancy}}) {
                    var event = new OutboxEvent();
                    event.id = UUID.randomUUID();
                    event.aggregateId = aggregateId;
                    event.eventType = eventType;
                    event.payload = payload;
                    event.status = "PENDING";
                    event.occurredAt = occurredAt;
                    event.nextAttemptAt = occurredAt;
                    {{#multiTenancy}}event.tenantId = tenantId;{{/multiTenancy}}
                    return event;
                }

                public void published(Instant time) { status = "PUBLISHED"; publishedAt = time; error = null; }
                public void failed(String sanitizedError, Instant now) {
                    attempts++;
                    status = attempts >= 10 ? "DEAD" : "PENDING";
                    error = sanitizedError == null ? "unknown" : sanitizedError.substring(0, Math.min(512, sanitizedError.length()));
                    nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
                }
                public UUID getId() { return id; }
                public String getAggregateId() { return aggregateId; }
                public String getEventType() { return eventType; }
                public String getPayload() { return payload; }
                public String getStatus() { return status; }
                public int getAttempts() { return attempts; }
            }
            """;
    private static final String OUTBOX_REPOSITORY =
            """
            package {{basePackage}}.shared.messaging.outbox;

            import java.util.List;
            import java.util.UUID;
            import java.time.Instant;
            import org.springframework.data.jpa.repository.JpaRepository;
            import org.springframework.data.jpa.repository.Modifying;
            import org.springframework.data.jpa.repository.Query;

            interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
                @Query(value = "SELECT * FROM outbox_event WHERE status = 'PENDING' "
                        + "AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY occurred_at "
                        + "FOR UPDATE SKIP LOCKED LIMIT 100", nativeQuery = true)
                List<OutboxEvent> lockPendingBatch();

                @Modifying
                @Query("delete from OutboxEvent event where event.status = 'PUBLISHED' and event.publishedAt < :before")
                int deletePublishedBefore(Instant before);
            }
            """;
    private static final String OUTBOX_PUBLISHER =
            """
            package {{basePackage}}.shared.messaging.outbox;

            import java.time.Clock;
            import java.util.concurrent.TimeUnit;
            import io.micrometer.core.instrument.MeterRegistry;
            import org.springframework.kafka.core.KafkaTemplate;
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            @Component
            public class OutboxPublisher {
                private final OutboxRepository repository;
                private final KafkaTemplate<String, Object> kafka;
                private final Clock clock;
                private final MeterRegistry metrics;

                public OutboxPublisher(
                        OutboxRepository repository,
                        KafkaTemplate<String, Object> kafka,
                        Clock clock,
                        MeterRegistry metrics) {
                    this.repository = repository;
                    this.kafka = kafka;
                    this.clock = clock;
                    this.metrics = metrics;
                }

                @Scheduled(fixedDelayString = "${backsmith.outbox.delay:1000}")
                @Transactional
                public void publishBatch() {
                    for (OutboxEvent event : repository.lockPendingBatch()) {
                        try {
                            kafka.send(event.getEventType(), event.getAggregateId(), event.getPayload())
                                    .get(10, TimeUnit.SECONDS);
                            event.published(clock.instant());
                            metrics.counter("backsmith.outbox.published").increment();
                        } catch (Exception failure) {
                            event.failed(failure.getClass().getSimpleName(), clock.instant());
                            metrics.counter("backsmith.outbox.failures").increment();
                        }
                    }
                }
            }
            """;
    private static final String AUDIT_LOGGER =
            """
            package {{basePackage}}.shared.observability;

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            import org.springframework.stereotype.Component;

            @Component
            public class AuditLogger {
                private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

                public void record(AuditEvent event) {
                    LOG.info(
                            "audit eventId={} action={} actorId={} subjectId={} occurredAt={}",
                            event.eventId(),
                            event.action(),
                            event.actorId(),
                            event.subjectId(),
                            event.occurredAt());
                }
            }
            """;
    private static final String TRANSACTIONAL_OUTBOX =
            """
            package {{basePackage}}.shared.messaging.outbox;

            {{#multiTenancy}}import {{basePackage}}.shared.tenancy.TenantContext;{{/multiTenancy}}
            import java.time.Clock;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            @Component
            public class TransactionalOutbox {
                private final OutboxRepository repository;
                private final Clock clock;

                public TransactionalOutbox(OutboxRepository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                @Transactional
                public void append(String aggregateId, String eventType, String jsonPayload) {
                    repository.save(OutboxEvent.pending(
                            aggregateId,
                            eventType,
                            jsonPayload,
                            clock.instant()
                            {{#multiTenancy}}, TenantContext.requiredTenant(){{/multiTenancy}}));
                }
            }
            """;
    private static final String OUTBOX_CLEANUP =
            """
            package {{basePackage}}.shared.messaging.outbox;

            import java.time.Clock;
            import java.time.Duration;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            @Component
            public class OutboxCleanup {
                private final OutboxRepository repository;
                private final Clock clock;
                private final Duration retention;

                public OutboxCleanup(
                        OutboxRepository repository,
                        Clock clock,
                        @Value("${backsmith.outbox.retention:P7D}") Duration retention) {
                    this.repository = repository;
                    this.clock = clock;
                    this.retention = retention;
                }

                @Scheduled(cron = "${backsmith.outbox.cleanup-cron:0 0 3 * * *}")
                @Transactional
                public void removePublishedEvents() {
                    repository.deletePublishedBefore(clock.instant().minus(retention));
                }
            }
            """;
    private static final String OUTBOX_TEST =
            """
            package {{basePackage}}.shared.messaging.outbox;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import java.time.Instant;
            import org.junit.jupiter.api.Test;

            class OutboxEventTest {
                @Test
                void retriesWithoutClaimingExactlyOnceDelivery() {
                    Instant now = Instant.parse("2026-01-01T00:00:00Z");
                    OutboxEvent event = OutboxEvent.pending(
                            "aggregate", "event.v1", "{}", now{{#multiTenancy}}, "tenant-a"{{/multiTenancy}});
                    event.failed("timeout", now);
                    assertEquals("PENDING", event.getStatus());
                    assertEquals(1, event.getAttempts());
                }
            }
            """;
    private static final String OUTBOX_MIGRATION =
            """
            CREATE TABLE outbox_event (
              id UUID PRIMARY KEY,
              aggregate_id VARCHAR(255) NOT NULL,
              event_type VARCHAR(255) NOT NULL,
              payload JSONB NOT NULL,
              status VARCHAR(32) NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0,
              occurred_at TIMESTAMPTZ NOT NULL,
              next_attempt_at TIMESTAMPTZ NOT NULL,
              published_at TIMESTAMPTZ,
              error VARCHAR(512){{#multiTenancy}},{{/multiTenancy}}
              {{#multiTenancy}}tenant_id VARCHAR(64) NOT NULL{{/multiTenancy}}
            );
            CREATE INDEX idx_outbox_pending ON outbox_event(status, next_attempt_at, occurred_at);
            """;
    private static final String CACHE_KEY_FACTORY =
            """
            package {{basePackage}}.shared.cache;

            import java.util.Objects;

            public final class CacheKeyFactory {
                private CacheKeyFactory() {}

                public static String key(String namespace, String tenantId, String identifier) {
                    Objects.requireNonNull(namespace, "namespace");
                    Objects.requireNonNull(identifier, "identifier");
                    String tenant = tenantId == null || tenantId.isBlank() ? "global" : tenantId;
                    return "backsmith:" + namespace + ":" + tenant + ":" + identifier;
                }
            }
            """;
    private static final String JDBC_IDEMPOTENT_EVENT_STORE =
            """
            package {{basePackage}}.shared.messaging;

            import java.time.Clock;
            import java.sql.Timestamp;
            import java.util.UUID;
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            @Component
            public class JdbcIdempotentEventStore implements IdempotentEventStore {
                private final JdbcTemplate database;
                private final Clock clock;

                public JdbcIdempotentEventStore(JdbcTemplate database, Clock clock) {
                    this.database = database;
                    this.clock = clock;
                }

                @Override
                @Transactional
                public boolean claim(UUID eventId, String consumer) {
                    return database.update(
                                    "INSERT INTO consumed_event(event_id, consumer, status) "
                                            + "VALUES (?, ?, 'PROCESSING') "
                                            + "ON CONFLICT (event_id, consumer) DO UPDATE "
                                            + "SET status = 'PROCESSING', error = NULL "
                                            + "WHERE consumed_event.status = 'FAILED'",
                                    eventId,
                                    consumer)
                            == 1;
                }

                @Override
                @Transactional
                public void complete(UUID eventId, String consumer) {
                    database.update(
                            "UPDATE consumed_event SET status = 'COMPLETED', processed_at = ?, error = NULL "
                                    + "WHERE event_id = ? AND consumer = ?",
                            Timestamp.from(clock.instant()),
                            eventId,
                            consumer);
                }

                @Override
                @Transactional
                public void release(UUID eventId, String consumer, String sanitizedError) {
                    String safe = sanitizedError == null
                            ? "unknown"
                            : sanitizedError.substring(0, Math.min(512, sanitizedError.length()));
                    database.update(
                            "UPDATE consumed_event SET status = 'FAILED', error = ? "
                                    + "WHERE event_id = ? AND consumer = ? AND status = 'PROCESSING'",
                            safe,
                            eventId,
                            consumer);
                }
            }
            """;
    private static final String CONSUMED_EVENT_MIGRATION =
            """
            CREATE TABLE consumed_event (
              event_id UUID NOT NULL,
              consumer VARCHAR(255) NOT NULL,
              status VARCHAR(32) NOT NULL,
              processed_at TIMESTAMPTZ,
              error VARCHAR(512),
              PRIMARY KEY (event_id, consumer)
            );
            """;
    private static final String REDIS_CACHE_STORE =
            """
            package {{basePackage}}.shared.cache;

            {{#multiTenancy}}import {{basePackage}}.shared.tenancy.TenantContext;{{/multiTenancy}}
            import com.fasterxml.jackson.core.JsonProcessingException;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import io.micrometer.core.instrument.MeterRegistry;
            import java.time.Duration;
            import java.util.Optional;
            import org.springframework.data.redis.core.StringRedisTemplate;
            import org.springframework.stereotype.Component;

            @Component
            public class RedisCacheStore {
                private final StringRedisTemplate redis;
                private final ObjectMapper json;
                private final MeterRegistry metrics;

                public RedisCacheStore(
                        StringRedisTemplate redis, ObjectMapper json, MeterRegistry metrics) {
                    this.redis = redis;
                    this.json = json;
                    this.metrics = metrics;
                }

                public <T> Optional<T> get(String namespace, String identifier, Class<T> type) {
                    String cached = redis.opsForValue().get(key(namespace, identifier));
                    metrics.counter("backsmith.cache." + (cached == null ? "miss" : "hit")).increment();
                    if (cached == null) return Optional.empty();
                    try {
                        return Optional.of(json.readValue(cached, type));
                    } catch (JsonProcessingException failure) {
                        evict(namespace, identifier);
                        return Optional.empty();
                    }
                }

                public void put(String namespace, String identifier, Object value, Duration ttl) {
                    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                        throw new IllegalArgumentException("cache TTL must be positive");
                    }
                    try {
                        redis.opsForValue().set(
                                key(namespace, identifier), json.writeValueAsString(value), ttl);
                    } catch (JsonProcessingException failure) {
                        throw new IllegalArgumentException("cache value is not serializable", failure);
                    }
                }

                public void evict(String namespace, String identifier) {
                    redis.delete(key(namespace, identifier));
                }

                private String key(String namespace, String identifier) {
                    String tenant = {{#multiTenancy}}TenantContext.requiredTenant(){{/multiTenancy}}{{^multiTenancy}}"global"{{/multiTenancy}};
                    return CacheKeyFactory.key(namespace, tenant, identifier);
                }
            }
            """;
    private static final String CACHE_KEY_FACTORY_TEST =
            """
            package {{basePackage}}.shared.cache;

            import static org.junit.jupiter.api.Assertions.assertNotEquals;
            import org.junit.jupiter.api.Test;

            class CacheKeyFactoryTest {
                @Test
                void isolatesTenantNamespaces() {
                    assertNotEquals(
                            CacheKeyFactory.key("customer", "tenant-a", "42"),
                            CacheKeyFactory.key("customer", "tenant-b", "42"));
                }
            }
            """;
    private static final String TENANT_CONTEXT =
            """
            package {{basePackage}}.shared.tenancy;

            public final class TenantContext {
                private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
                private TenantContext() {}
                public static String requiredTenant() {
                    String tenant = CURRENT.get();
                    if (tenant == null) throw new IllegalStateException("tenant context is not available");
                    return tenant;
                }
                static void set(String tenant) { CURRENT.set(tenant); }
                static void clear() { CURRENT.remove(); }
            }
            """;
    private static final String TENANT_RESOLVER =
            """
            package {{basePackage}}.shared.tenancy;

            import jakarta.servlet.http.HttpServletRequest;
            import java.util.Optional;

            public interface TenantResolver {
                Optional<String> resolve(HttpServletRequest request);
            }
            """;
    private static final String HEADER_TENANT_RESOLVER =
            """
            package {{basePackage}}.shared.tenancy;

            import jakarta.servlet.http.HttpServletRequest;
            import java.util.Optional;
            import org.springframework.stereotype.Component;

            @Component
            public class HeaderTenantResolver implements TenantResolver {
                public Optional<String> resolve(HttpServletRequest request) {
                    return Optional.ofNullable(request.getHeader("X-Tenant-Id"))
                            .filter(value -> value.matches("[a-zA-Z0-9_-]{1,64}"));
                }
            }
            """;
    private static final String TENANT_MEMBERSHIP =
            """
            package {{basePackage}}.shared.tenancy;

            import org.springframework.security.core.Authentication;

            public interface TenantMembership {
                boolean canAccess(Authentication authentication, String tenantId);
            }
            """;
    private static final String JWT_TENANT_MEMBERSHIP =
            """
            package {{basePackage}}.shared.tenancy;

            import java.util.List;
            import org.springframework.security.core.Authentication;
            import org.springframework.security.oauth2.jwt.Jwt;
            import org.springframework.stereotype.Component;

            @Component
            public class JwtTenantMembership implements TenantMembership {
                @Override
                public boolean canAccess(Authentication authentication, String tenantId) {
                    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                        return false;
                    }
                    List<String> memberships = jwt.getClaimAsStringList("tenants");
                    return memberships != null && memberships.contains(tenantId);
                }
            }
            """;
    private static final String TENANT_FILTER =
            """
            package {{basePackage}}.shared.tenancy;

            import jakarta.servlet.*;
            import jakarta.servlet.http.*;
            import java.io.IOException;
            import org.springframework.core.Ordered;
            import org.springframework.core.annotation.Order;
            import org.springframework.http.HttpStatus;
            import org.springframework.security.core.context.SecurityContextHolder;
            import org.springframework.stereotype.Component;
            import org.springframework.web.filter.OncePerRequestFilter;

            @Component
            @Order(Ordered.LOWEST_PRECEDENCE)
            public class TenantFilter extends OncePerRequestFilter {
                private final TenantResolver resolver;
                private final TenantMembership membership;
                public TenantFilter(TenantResolver resolver, TenantMembership membership) {
                    this.resolver = resolver;
                    this.membership = membership;
                }

                @Override
                protected boolean shouldNotFilter(HttpServletRequest request) {
                    String path = request.getRequestURI();
                    return path.startsWith("/api/v1/auth/") || path.startsWith("/actuator/");
                }

                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    var tenant = resolver.resolve(request);
                    if (tenant.isEmpty()) {
                        response.sendError(HttpStatus.BAD_REQUEST.value(), "A valid tenant is required");
                        return;
                    }
                    if (!membership.canAccess(
                            SecurityContextHolder.getContext().getAuthentication(), tenant.get())) {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Tenant access denied");
                        return;
                    }
                    try {
                        TenantContext.set(tenant.get());
                        chain.doFilter(request, response);
                    } finally {
                        TenantContext.clear();
                    }
                }
            }
            """;
    private static final String ARCHITECTURE_TEST =
            """
            package {{basePackage}};

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
            {{#modularMonolith}}import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;{{/modularMonolith}}
            import com.tngtech.archunit.core.importer.ImportOption;
            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            @AnalyzeClasses(packages = "{{basePackage}}", importOptions = ImportOption.DoNotIncludeTests.class)
            class ArchitectureTest {
                {{^layered}}
                @ArchTest
                static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");
                {{/layered}}

                @ArchTest
                static final ArchRule CONTROLLERS_DO_NOT_ACCESS_PERSISTENCE = noClasses()
                        .that().resideInAnyPackage("..controller..", "..api..", "..web..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("..repository..", "..persistence..");

                @ArchTest
                static final ArchRule SHARED_CODE_DOES_NOT_DEPEND_ON_MODULES = noClasses()
                        .that().resideInAPackage("..shared..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("..modules..");

                {{#modularMonolith}}
                @ArchTest
                static final ArchRule MODULES_ARE_ISOLATED = slices()
                        .matching("..modules.(*)..")
                        .should().notDependOnEachOther();
                {{/modularMonolith}}
            }
            """;
    private static final String POSTGRES_INTEGRATION_TEST =
            """
            package {{basePackage}};

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;
            import java.util.UUID;
            import java.util.concurrent.CountDownLatch;
            import java.util.concurrent.Executors;
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.dao.DataIntegrityViolationException;
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.test.context.DynamicPropertyRegistry;
            import org.springframework.test.context.DynamicPropertySource;
            {{#starterAuth}}import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;{{/starterAuth}}
            {{#starterAuth}}import org.springframework.http.MediaType;{{/starterAuth}}
            {{#starterAuth}}import org.springframework.test.web.servlet.MockMvc;{{/starterAuth}}
            {{#starterAuth}}import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;{{/starterAuth}}
            {{#starterAuth}}import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;{{/starterAuth}}
            {{#starterAuth}}import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;{{/starterAuth}}
            import org.testcontainers.junit.jupiter.Container;
            import org.testcontainers.junit.jupiter.Testcontainers;
            import org.testcontainers.postgresql.PostgreSQLContainer;

            @Testcontainers(disabledWithoutDocker = true)
            @SpringBootTest(properties = "app.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes")
            {{#starterAuth}}@AutoConfigureMockMvc{{/starterAuth}}
            class PostgreSqlIntegrationTest {
                private final JdbcTemplate jdbc;
                {{#starterAuth}}
                private final MockMvc mockMvc;
                {{/starterAuth}}

                @org.springframework.beans.factory.annotation.Autowired
                PostgreSqlIntegrationTest(JdbcTemplate jdbc{{#starterAuth}}, MockMvc mockMvc{{/starterAuth}}) {
                    this.jdbc = jdbc;
                    {{#starterAuth}}
                    this.mockMvc = mockMvc;
                    {{/starterAuth}}
                }

                @Container
                static final PostgreSQLContainer POSTGRES =
                        new PostgreSQLContainer("postgres:17-alpine");

                @DynamicPropertySource
                static void databaseProperties(DynamicPropertyRegistry properties) {
                    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                    properties.add("spring.datasource.username", POSTGRES::getUsername);
                    properties.add("spring.datasource.password", POSTGRES::getPassword);
                }

                @Test void databaseIsReachable() {
                    assertTrue(POSTGRES.isRunning());
                }

                @Test
                void duplicateConcurrentIdempotencyKeysAreRejected() throws Exception {
                    String key = "concurrent-" + UUID.randomUUID();
                    var ready = new CountDownLatch(2);
                    var start = new CountDownLatch(1);
                    java.util.concurrent.Callable<Boolean> insert = () -> {
                        ready.countDown();
                        start.await();
                        try {
                            jdbc.update(
                                    "INSERT INTO idempotency_record "
                                            + "(id, tenant_id, key_value, request_hash, state, created_at, expires_at, version) "
                                            + "VALUES (?, 'default', ?, ?, 'PROCESSING', CURRENT_TIMESTAMP, "
                                            + "CURRENT_TIMESTAMP + INTERVAL '1 hour', 0)",
                                    UUID.randomUUID(),
                                    key,
                                    "a".repeat(64));
                            return true;
                        } catch (DataIntegrityViolationException duplicate) {
                            return false;
                        }
                    };
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        var first = executor.submit(insert);
                        var second = executor.submit(insert);
                        ready.await();
                        start.countDown();
                        long successes =
                                java.util.stream.Stream.of(first.get(), second.get())
                                        .filter(Boolean::booleanValue)
                                        .count();
                        assertEquals(1, successes);
                    }
                }

                {{#starterAuth}}
                @Test
                void registersAndAuthenticatesAnAccount() throws Exception {
                    String email = "user-" + UUID.randomUUID() + "@example.com";
                    String registration =
                            "{\\\"email\\\":\\\"" + email
                                    + "\\\",\\\"password\\\":\\\"a-production-password\\\",\\\"tenantId\\\":\\\"tenant-a\\\"}";
                    mockMvc.perform(post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registration))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.email").value(email));

                    String login =
                            "{\\\"email\\\":\\\"" + email
                                    + "\\\",\\\"password\\\":\\\"a-production-password\\\"}";
                    mockMvc.perform(post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(login))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.accessToken").isString())
                            .andExpect(jsonPath("$.refreshToken").isString());
                }
                {{/starterAuth}}
            }
            """;
    private static final String JWT_TENANT_MEMBERSHIP_TEST =
            """
            package {{basePackage}}.shared.tenancy;

            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertTrue;
            import java.util.List;
            import org.junit.jupiter.api.Test;
            import org.springframework.security.oauth2.jwt.Jwt;
            import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

            class JwtTenantMembershipTest {
                private final JwtTenantMembership membership = new JwtTenantMembership();

                @Test
                void enforcesClaimMembership() {
                    Jwt jwt = Jwt.withTokenValue("test")
                            .header("alg", "none")
                            .subject("account")
                            .claim("tenants", List.of("tenant-a", "tenant-b"))
                            .build();
                    var authentication = new JwtAuthenticationToken(jwt);

                    assertTrue(membership.canAccess(authentication, "tenant-a"));
                    assertFalse(membership.canAccess(authentication, "tenant-c"));
                    assertFalse(membership.canAccess(null, "tenant-a"));
                }
            }
            """;
    private static final String APPLICATION_TEST =
            """
            package {{basePackage}};
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
            class ApplicationTest {
                @Test void applicationClassLoads() { assertDoesNotThrow(() -> Class.forName("{{basePackage}}.Application")); }
            }
            """;
    private static final String DOCKERFILE =
            """
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
    private static final String COMPOSE =
            """
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
              {{#redis}}
              redis:
                image: redis:8-alpine
                command: ["redis-server", "--appendonly", "yes"]
                ports: ["6379:6379"]
                healthcheck:
                  test: ["CMD", "redis-cli", "ping"]
                  interval: 5s
                  timeout: 3s
                  retries: 10
              {{/redis}}
              {{#kafka}}
              kafka:
                image: apache/kafka:4.1.0
                ports: ["9092:9092"]
                environment:
                  KAFKA_NODE_ID: 1
                  KAFKA_PROCESS_ROLES: broker,controller
                  KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
                  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
                  KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
                  KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
              {{/kafka}}
            volumes:
              postgres-data:
            """;
    private static final String WORKFLOW =
            """
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
                  - uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4
                    with:
                      name: application-jar
                      path: target/*.jar
                      if-no-files-found: error
              dependency-review:
                if: github.event_name == 'pull_request'
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
                  - uses: actions/dependency-review-action@a1d282b36b6f3519aa1f3fc636f609c47dddb294 # v5.0.0
              container:
                needs: verify
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
                  - uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f # v3
                  - uses: docker/build-push-action@10e90e3645eae34f1e60eeb005ba3a3d33f178e8 # v6
                    with:
                      context: .
                      push: false
                      tags: {{artifactId}}:ci
            """;
    private static final String GENERATED_README =
            """
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
    private static final String DOCKERIGNORE = "target/\n.git/\n.idea/\n.env\n*.log\n";
    private static final String EDITORCONFIG =
            "root = true\n\n[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\nindent_style = space\nindent_size = 2\n";
    private static final String WRAPPER_PROPERTIES =
            "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip\n";
    private static final String GENERATED_MVNW =
            """
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
    private static final String GENERATED_CHECKSTYLE =
            """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                    "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
              <property name="charset" value="UTF-8"/>
              <module name="FileTabCharacter"/>
              <module name="NewlineAtEndOfFile"/>
              <module name="TreeWalker">
                <module name="IllegalImport"/>
                <module name="UnusedImports"/>
              </module>
            </module>
            """;
    private static final String GENERATED_MVNW_CMD =
            """
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
    private static final String ENV =
            """
            DATABASE_URL=jdbc:postgresql://localhost:5432/{{artifactId}}
            DATABASE_USERNAME=postgres
            DATABASE_PASSWORD=change-me
            APP_ALLOWED_ORIGINS=http://localhost:3000
            JWT_SECRET=replace-with-at-least-32-random-bytes
            JWT_ISSUER_URI=https://issuer.example.invalid
            JWT_AUDIENCE={{artifactId}}
            KAFKA_BOOTSTRAP_SERVERS=localhost:9092
            REDIS_HOST=localhost
            REDIS_PORT=6379
            """;
    private static final String KUBERNETES_DEPLOYMENT =
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: {{artifactId}}
            spec:
              replicas: 2
              selector:
                matchLabels: {app: {{artifactId}}}
              template:
                metadata:
                  labels: {app: {{artifactId}}}
                spec:
                  serviceAccountName: {{artifactId}}
                  securityContext:
                    runAsNonRoot: true
                  containers:
                    - name: application
                      image: example.invalid/{{artifactId}}:replace-me
                      ports: [{containerPort: 8080}]
                      envFrom:
                        - configMapRef: {name: {{artifactId}}}
                        - secretRef: {name: {{artifactId}}}
                      readinessProbe:
                        httpGet: {path: /actuator/health/readiness, port: 8080}
                        initialDelaySeconds: 10
                      livenessProbe:
                        httpGet: {path: /actuator/health/liveness, port: 8080}
                        initialDelaySeconds: 30
                      resources:
                        requests: {cpu: 100m, memory: 256Mi}
                        limits: {cpu: "1", memory: 1Gi}
            ---
            apiVersion: v1
            kind: ServiceAccount
            metadata:
              name: {{artifactId}}
            """;
    private static final String KUBERNETES_SERVICE =
            """
            apiVersion: v1
            kind: Service
            metadata:
              name: {{artifactId}}
            spec:
              selector: {app: {{artifactId}}}
              ports:
                - port: 80
                  targetPort: 8080
            """;
    private static final String KUBERNETES_CONFIGMAP =
            """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: {{artifactId}}
            data:
              SPRING_PROFILES_ACTIVE: prod
              DATABASE_URL: jdbc:postgresql://postgres:5432/{{artifactId}}
            """;
    private static final String KUBERNETES_SECRET =
            """
            apiVersion: v1
            kind: Secret
            metadata:
              name: {{artifactId}}
            type: Opaque
            stringData:
              DATABASE_USERNAME: replace-me
              DATABASE_PASSWORD: replace-me
              JWT_ISSUER_URI: replace-me
            """;
    private static final String KUBERNETES_HPA =
            """
            apiVersion: autoscaling/v2
            kind: HorizontalPodAutoscaler
            metadata:
              name: {{artifactId}}
            spec:
              scaleTargetRef: {apiVersion: apps/v1, kind: Deployment, name: {{artifactId}}}
              minReplicas: 2
              maxReplicas: 10
              metrics:
                - type: Resource
                  resource:
                    name: cpu
                    target: {type: Utilization, averageUtilization: 70}
            """;
    private static final String KUBERNETES_PDB =
            """
            apiVersion: policy/v1
            kind: PodDisruptionBudget
            metadata:
              name: {{artifactId}}
            spec:
              minAvailable: 1
              selector:
                matchLabels: {app: {{artifactId}}}
            """;
    private static final String KUBERNETES_INGRESS =
            """
            apiVersion: networking.k8s.io/v1
            kind: Ingress
            metadata:
              name: {{artifactId}}
            spec:
              rules:
                - host: {{artifactId}}.example.invalid
                  http:
                    paths:
                      - path: /
                        pathType: Prefix
                        backend:
                          service:
                            name: {{artifactId}}
                            port: {number: 80}
            """;
}

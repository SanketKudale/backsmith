package io.backsmith.adapter.spring;

import io.backsmith.model.Architecture;
import io.backsmith.model.FieldDefinition;
import io.backsmith.model.FieldDefinition.FieldType;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SpringComponentGenerator {
    public Map<Path, String> generate(
            ProjectConfiguration configuration,
            String generator,
            String name,
            String module,
            List<String> fieldSpecifications,
            String subtype) {
        Objects.requireNonNull(configuration, "configuration");
        String safeModule = safePackage(module == null ? "shared" : module);
        String safeName = name == null ? null : safeType(name);
        List<FieldDefinition> fields =
                fieldSpecifications == null
                        ? List.of()
                        : fieldSpecifications.stream().map(FieldDefinition::parse).toList();
        return switch (generator) {
            case "module" -> module(configuration, safeModule);
            case "entity" ->
                    entity(configuration, requiredName(safeName, generator), safeModule, fields);
            case "value-object" ->
                    valueObject(
                            configuration, requiredName(safeName, generator), safeModule, subtype);
            case "usecase" ->
                    useCase(configuration, requiredName(safeName, generator), safeModule, subtype);
            case "controller" ->
                    controller(configuration, requiredName(safeName, generator), safeModule);
            case "repository" ->
                    repository(configuration, requiredName(safeName, generator), safeModule);
            case "service" -> service(configuration, requiredName(safeName, generator), safeModule);
            case "migration" -> migration(configuration, requiredName(safeName, generator), fields);
            case "event" ->
                    event(configuration, requiredName(safeName, generator), safeModule, fields);
            case "producer" ->
                    producer(configuration, requiredName(safeName, generator), safeModule);
            case "consumer" ->
                    consumer(configuration, requiredName(safeName, generator), safeModule);
            case "scheduler" ->
                    scheduler(configuration, requiredName(safeName, generator), safeModule);
            case "integration" ->
                    integration(
                            configuration, requiredName(safeName, generator), safeModule, subtype);
            case "api", "api-dir" ->
                    throw new IllegalArgumentException(
                            generator
                                    + " requires an OpenAPI contract and is handled by the contract generator");
            case "docker" -> docker(configuration);
            case "ci" -> ci();
            case "docs" -> docs(configuration);
            default -> throw new IllegalArgumentException("unsupported generator: " + generator);
        };
    }

    private Map<Path, String> module(ProjectConfiguration configuration, String module) {
        String packageName = configuration.project().basePackage() + ".modules." + module;
        String type = capitalize(module) + "Module";
        return files(
                source(packageName, "package-info.java"),
                """
                /**
                 * %s business module. Other modules must use its public application contracts.
                 */
                package %s;
                """
                        .formatted(capitalize(module), packageName),
                source(packageName, type + ".java"),
                """
                package %s;

                /** Stable marker used by architecture and component scanning tests. */
                public final class %s {
                    private %s() {}
                }
                """
                        .formatted(packageName, type, type));
    }

    private Map<Path, String> entity(
            ProjectConfiguration configuration,
            String name,
            String module,
            List<FieldDefinition> suppliedFields) {
        List<FieldDefinition> fields = withIdentifier(suppliedFields);
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        DatabaseProfile database = DatabaseProfile.from(configuration.features().database());
        if (database.mongodb()) {
            return configuration.architecture().type() == Architecture.LAYERED
                    ? layeredMongoEntity(layout, name, fields)
                    : portBasedMongoEntity(layout, name, fields);
        }
        return configuration.architecture().type() == Architecture.LAYERED
                ? layeredEntity(layout, name, fields, database)
                : portBasedEntity(layout, name, fields, database);
    }

    private Map<Path, String> layeredEntity(
            Layout layout, String name, List<FieldDefinition> fields, DatabaseProfile database) {
        var generated = new LinkedHashMap<Path, String>();
        addEnums(generated, layout.domainPackage(), name, fields);
        generated.put(
                source(layout.domainPackage(), name + ".java"),
                jpaEntity(layout.domainPackage(), layout.domainPackage(), name, fields, database));
        String idType = javaType(fields.getFirst(), name, false);
        generated.put(
                source(layout.repositoryPackage(), name + "Repository.java"),
                """
                package %s;

                import %s.%s;
                import %s;
                import org.springframework.data.jpa.repository.JpaRepository;

                public interface %sRepository extends JpaRepository<%s, %s> {}
                """
                        .formatted(
                                layout.repositoryPackage(),
                                layout.domainPackage(),
                                name,
                                importFor(idType),
                                name,
                                name,
                                simpleType(idType)));
        generated.putAll(entityMigration(name, fields));
        generated.put(
                testSource(layout.domainPackage(), name + "Test.java"),
                entityTypeTest(layout.domainPackage(), name));
        return Map.copyOf(generated);
    }

    private Map<Path, String> portBasedEntity(
            Layout layout, String name, List<FieldDefinition> fields, DatabaseProfile database) {
        var generated = new LinkedHashMap<Path, String>();
        addEnums(generated, layout.domainPackage(), name, fields);
        generated.put(
                source(layout.domainPackage(), name + "Id.java"),
                """
                package %s;

                import java.util.Objects;
                import java.util.UUID;

                public record %sId(UUID value) {
                    public %sId { Objects.requireNonNull(value, "value"); }
                    public static %sId random() { return new %sId(UUID.randomUUID()); }
                }
                """
                        .formatted(layout.domainPackage(), name, name, name, name));
        generated.put(
                source(layout.domainPackage(), name + ".java"),
                domainRecord(layout.domainPackage(), name, fields));
        generated.put(
                source(layout.repositoryPackage(), name + "Repository.java"),
                """
                package %s;

                import %s.%s;
                import %s.%sId;
                import java.util.Optional;

                public interface %sRepository {
                    %s save(%s aggregate);
                    Optional<%s> findById(%sId id);
                }
                """
                        .formatted(
                                layout.repositoryPackage(),
                                layout.domainPackage(),
                                name,
                                layout.domainPackage(),
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
        String persistencePackage = layout.persistencePackage();
        generated.put(
                source(persistencePackage, name + "JpaEntity.java"),
                jpaEntity(
                        persistencePackage,
                        layout.domainPackage(),
                        name + "JpaEntity",
                        fields,
                        database));
        generated.put(
                source(persistencePackage, name + "JpaRepository.java"),
                """
                package %s;

                import java.util.UUID;
                import org.springframework.data.jpa.repository.JpaRepository;

                interface %sJpaRepository extends JpaRepository<%sJpaEntity, UUID> {}
                """
                        .formatted(persistencePackage, name, name));
        generated.put(
                source(persistencePackage, name + "PersistenceMapper.java"),
                persistenceMapper(layout, name, fields));
        generated.put(
                source(persistencePackage, name + "PersistenceAdapter.java"),
                """
                package %s;

                import %s.%s;
                import %s.%sId;
                import %s.%sRepository;
                import java.util.Optional;
                import org.springframework.stereotype.Repository;

                @Repository
                public class %sPersistenceAdapter implements %sRepository {
                    private final %sJpaRepository repository;
                    private final %sPersistenceMapper mapper;

                    public %sPersistenceAdapter(%sJpaRepository repository, %sPersistenceMapper mapper) {
                        this.repository = repository;
                        this.mapper = mapper;
                    }

                    @Override
                    public %s save(%s aggregate) {
                        return mapper.toDomain(repository.save(mapper.toEntity(aggregate)));
                    }

                    @Override
                    public Optional<%s> findById(%sId id) {
                        return repository.findById(id.value()).map(mapper::toDomain);
                    }
                }
                """
                        .formatted(
                                persistencePackage,
                                layout.domainPackage(),
                                name,
                                layout.domainPackage(),
                                name,
                                layout.repositoryPackage(),
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
        generated.putAll(entityMigration(name, fields));
        generated.put(
                testSource(layout.domainPackage(), name + "IdTest.java"),
                """
                package %s;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import java.util.UUID;
                import org.junit.jupiter.api.Test;

                class %sIdTest {
                    @Test void retainsItsValue() {
                        UUID value = UUID.randomUUID();
                        assertEquals(value, new %sId(value).value());
                    }
                }
                """
                        .formatted(layout.domainPackage(), name, name));
        return Map.copyOf(generated);
    }

    private Map<Path, String> layeredMongoEntity(
            Layout layout, String name, List<FieldDefinition> fields) {
        var generated = new LinkedHashMap<Path, String>();
        addEnums(generated, layout.domainPackage(), name, fields);
        generated.put(
                source(layout.domainPackage(), name + ".java"),
                mongoDocument(layout.domainPackage(), layout.domainPackage(), name, fields));
        String idType = javaType(fields.getFirst(), name, false);
        generated.put(
                source(layout.repositoryPackage(), name + "Repository.java"),
                """
                package %s;

                import %s.%s;
                import %s;
                import org.springframework.data.mongodb.repository.MongoRepository;

                public interface %sRepository extends MongoRepository<%s, %s> {}
                """
                        .formatted(
                                layout.repositoryPackage(),
                                layout.domainPackage(),
                                name,
                                importFor(idType),
                                name,
                                name,
                                simpleType(idType)));
        generated.put(
                testSource(layout.domainPackage(), name + "Test.java"),
                entityTypeTest(layout.domainPackage(), name));
        return Map.copyOf(generated);
    }

    private Map<Path, String> portBasedMongoEntity(
            Layout layout, String name, List<FieldDefinition> fields) {
        var generated = new LinkedHashMap<Path, String>();
        addEnums(generated, layout.domainPackage(), name, fields);
        generated.put(
                source(layout.domainPackage(), name + "Id.java"),
                """
                package %s;

                import java.util.Objects;
                import java.util.UUID;

                public record %sId(UUID value) {
                    public %sId { Objects.requireNonNull(value, "value"); }
                    public static %sId random() { return new %sId(UUID.randomUUID()); }
                }
                """
                        .formatted(layout.domainPackage(), name, name, name, name));
        generated.put(
                source(layout.domainPackage(), name + ".java"),
                domainRecord(layout.domainPackage(), name, fields));
        generated.put(
                source(layout.repositoryPackage(), name + "Repository.java"),
                """
                package %s;

                import %s.%s;
                import %s.%sId;
                import java.util.Optional;

                public interface %sRepository {
                    %s save(%s aggregate);
                    Optional<%s> findById(%sId id);
                }
                """
                        .formatted(
                                layout.repositoryPackage(),
                                layout.domainPackage(),
                                name,
                                layout.domainPackage(),
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
        String persistencePackage = layout.persistencePackage();
        generated.put(
                source(persistencePackage, name + "MongoDocument.java"),
                mongoDocument(
                        persistencePackage,
                        layout.domainPackage(),
                        name + "MongoDocument",
                        fields));
        generated.put(
                source(persistencePackage, name + "MongoRepository.java"),
                """
                package %s;

                import java.util.UUID;
                import org.springframework.data.mongodb.repository.MongoRepository;

                interface %sMongoRepository extends MongoRepository<%sMongoDocument, UUID> {}
                """
                        .formatted(persistencePackage, name, name));
        generated.put(
                source(persistencePackage, name + "PersistenceMapper.java"),
                mongoPersistenceMapper(layout, name, fields));
        generated.put(
                source(persistencePackage, name + "PersistenceAdapter.java"),
                """
                package %s;

                import %s.%s;
                import %s.%sId;
                import %s.%sRepository;
                import java.util.Optional;
                import org.springframework.stereotype.Repository;

                @Repository
                public class %sPersistenceAdapter implements %sRepository {
                    private final %sMongoRepository repository;
                    private final %sPersistenceMapper mapper;

                    public %sPersistenceAdapter(%sMongoRepository repository, %sPersistenceMapper mapper) {
                        this.repository = repository;
                        this.mapper = mapper;
                    }

                    @Override
                    public %s save(%s aggregate) {
                        return mapper.toDomain(repository.save(mapper.toDocument(aggregate)));
                    }

                    @Override
                    public Optional<%s> findById(%sId id) {
                        return repository.findById(id.value()).map(mapper::toDomain);
                    }
                }
                """
                        .formatted(
                                persistencePackage,
                                layout.domainPackage(),
                                name,
                                layout.domainPackage(),
                                name,
                                layout.repositoryPackage(),
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
        generated.put(
                testSource(layout.domainPackage(), name + "IdTest.java"),
                """
                package %s;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import java.util.UUID;
                import org.junit.jupiter.api.Test;

                class %sIdTest {
                    @Test void retainsItsValue() {
                        UUID value = UUID.randomUUID();
                        assertEquals(value, new %sId(value).value());
                    }
                }
                """
                        .formatted(layout.domainPackage(), name, name));
        return Map.copyOf(generated);
    }

    private String mongoDocument(
            String packageName, String enumPackage, String name, List<FieldDefinition> fields) {
        String collection = snake(name.replace("MongoDocument", ""));
        String entityName = name.replace("MongoDocument", "");
        String enumImports =
                fields.stream()
                        .filter(field -> field.type() == FieldType.ENUM)
                        .map(
                                field ->
                                        "import "
                                                + enumPackage
                                                + "."
                                                + entityName
                                                + capitalize(field.name())
                                                + ";")
                        .collect(java.util.stream.Collectors.joining("\n"));
        String imports =
                requiredImports(fields, true, "")
                        + (enumImports.isBlank() ? "" : enumImports + "\n");
        var declarations = new StringBuilder();
        var parameters = new ArrayList<String>();
        var assignments = new StringBuilder();
        var getters = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            FieldDefinition field = fields.get(index);
            String type = javaType(field, entityName, true);
            if (index == 0) declarations.append("    @Id\n");
            declarations
                    .append("    @Field(\"")
                    .append(snake(field.name()))
                    .append("\")\n    private ")
                    .append(type)
                    .append(' ')
                    .append(field.name())
                    .append(";\n\n");
            parameters.add(type + " " + field.name());
            assignments
                    .append("        this.")
                    .append(field.name())
                    .append(" = ")
                    .append(field.name())
                    .append(";\n");
            getters.append("    public ")
                    .append(type)
                    .append(" get")
                    .append(capitalize(field.name()))
                    .append("() { return ")
                    .append(field.name())
                    .append("; }\n");
        }
        return """
                package %s;

                %s
                import org.springframework.data.annotation.Id;
                import org.springframework.data.mongodb.core.mapping.Document;
                import org.springframework.data.mongodb.core.mapping.Field;

                @Document("%s")
                public class %s {
                %s    protected %s() {}

                    public %s(%s) {
                %s    }

                %s}
                """
                .formatted(
                        packageName,
                        imports,
                        collection,
                        name,
                        declarations,
                        name,
                        name,
                        String.join(", ", parameters),
                        assignments,
                        getters);
    }

    private String mongoPersistenceMapper(
            Layout layout, String name, List<FieldDefinition> fields) {
        var toDomain = new ArrayList<String>();
        toDomain.add(
                "new " + name + "Id(document.get" + capitalize(fields.getFirst().name()) + "())");
        fields.stream()
                .skip(1)
                .forEach(field -> toDomain.add("document.get" + capitalize(field.name()) + "()"));
        var toDocument = new ArrayList<String>();
        toDocument.add("aggregate.id().value()");
        fields.stream()
                .skip(1)
                .forEach(field -> toDocument.add("aggregate." + field.name() + "()"));
        return """
                package %s;

                import %s.%s;
                import %s.%sId;
                import org.springframework.stereotype.Component;

                @Component
                class %sPersistenceMapper {
                    %s toDomain(%sMongoDocument document) {
                        return new %s(
                                %s);
                    }

                    %sMongoDocument toDocument(%s aggregate) {
                        return new %sMongoDocument(
                                %s);
                    }
                }
                """
                .formatted(
                        layout.persistencePackage(),
                        layout.domainPackage(),
                        name,
                        layout.domainPackage(),
                        name,
                        name,
                        name,
                        name,
                        name,
                        String.join(",\n                ", toDomain),
                        name,
                        name,
                        name,
                        String.join(",\n                ", toDocument));
    }

    private String domainRecord(String packageName, String name, List<FieldDefinition> fields) {
        var components = new ArrayList<String>();
        components.add(name + "Id id");
        fields.stream()
                .skip(1)
                .forEach(field -> components.add(javaType(field, name, true) + " " + field.name()));
        var validation = new StringBuilder("        Objects.requireNonNull(id, \"id\");\n");
        fields.stream()
                .skip(1)
                .filter(FieldDefinition::required)
                .forEach(
                        field -> {
                            if (field.type() == FieldType.STRING
                                    || field.type() == FieldType.TEXT
                                    || field.type() == FieldType.EMAIL) {
                                validation
                                        .append("        if (")
                                        .append(field.name())
                                        .append(" == null || ")
                                        .append(field.name())
                                        .append(".isBlank()) throw new IllegalArgumentException(\"")
                                        .append(field.name())
                                        .append(" must not be blank\");\n");
                            } else if (!isPrimitive(field)) {
                                validation
                                        .append("        Objects.requireNonNull(")
                                        .append(field.name())
                                        .append(", \"")
                                        .append(field.name())
                                        .append("\");\n");
                            }
                        });
        String imports =
                requiredImports(
                        fields.stream().skip(1).toList(), true, "import java.util.Objects;\n");
        return """
                package %s;

                %s

                public record %s(
                        %s) {
                    public %s {
                %s    }
                }
                """
                .formatted(
                        packageName,
                        imports,
                        name,
                        String.join(",\n        ", components),
                        name,
                        validation);
    }

    private String jpaEntity(
            String packageName,
            String enumPackage,
            String name,
            List<FieldDefinition> fields,
            DatabaseProfile database) {
        String table = snake(name.replace("JpaEntity", ""));
        String entityName = name.replace("JpaEntity", "");
        String enumImports =
                fields.stream()
                        .filter(field -> field.type() == FieldType.ENUM)
                        .map(
                                field ->
                                        "import "
                                                + enumPackage
                                                + "."
                                                + entityName
                                                + capitalize(field.name())
                                                + ";")
                        .collect(java.util.stream.Collectors.joining("\n"));
        String imports =
                requiredImports(fields, false, "")
                        + (enumImports.isBlank() ? "" : enumImports + "\n");
        var declarations = new StringBuilder();
        var parameters = new ArrayList<String>();
        var assignments = new StringBuilder();
        var getters = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            FieldDefinition field = fields.get(index);
            String type = javaType(field, name.replace("JpaEntity", ""), false);
            if (index == 0) {
                declarations.append(
                        "    @Id\n    @GeneratedValue(strategy = GenerationType.UUID)\n");
            }
            if (field.type() == FieldType.ENUM) {
                declarations.append("    @Enumerated(EnumType.STRING)\n");
            }
            if (field.type() == FieldType.TEXT) {
                declarations.append("    @Lob\n");
            }
            if (field.type() == FieldType.BINARY) {
                declarations.append("    @Lob\n");
            }
            declarations.append("    @Column(name = \"").append(snake(field.name())).append("\"");
            if (field.required()) declarations.append(", nullable = false");
            if (field.unique()) declarations.append(", unique = true");
            if (field.type() == FieldType.DECIMAL) {
                declarations
                        .append(", precision = ")
                        .append(field.options().get("precision"))
                        .append(", scale = ")
                        .append(field.options().get("scale"));
            }
            if (field.options().containsKey("max")) {
                declarations.append(", length = ").append(field.options().get("max"));
            } else if (field.options().containsKey("length")) {
                declarations.append(", length = ").append(field.options().get("length"));
            }
            if (field.type() == FieldType.JSON || field.type() == FieldType.LIST)
                declarations
                        .append(", columnDefinition = \"")
                        .append(database.jsonType())
                        .append("\"");
            declarations
                    .append(")\n    private ")
                    .append(simpleType(type))
                    .append(' ')
                    .append(field.name())
                    .append(";\n\n");
            parameters.add(simpleType(type) + " " + field.name());
            assignments
                    .append("        this.")
                    .append(field.name())
                    .append(" = ")
                    .append(field.name())
                    .append(";\n");
            getters.append("    public ")
                    .append(simpleType(type))
                    .append(" get")
                    .append(capitalize(field.name()))
                    .append("() { return ")
                    .append(field.name())
                    .append("; }\n");
        }
        return """
                package %s;

                import jakarta.persistence.*;
                %s

                @Entity
                @Table(name = "%s")
                public class %s {
                %s    protected %s() {}

                    public %s(%s) {
                %s    }

                %s}
                """
                .formatted(
                        packageName,
                        imports,
                        table,
                        name,
                        declarations,
                        name,
                        name,
                        String.join(", ", parameters),
                        assignments,
                        getters);
    }

    private String requiredImports(
            List<FieldDefinition> fields, boolean includeLists, String required) {
        var imports = new StringBuilder(required);
        if (fields.stream().anyMatch(field -> field.type() == FieldType.DECIMAL)) {
            imports.append("import java.math.BigDecimal;\n");
        }
        if (fields.stream().anyMatch(field -> field.type() == FieldType.INSTANT)) {
            imports.append("import java.time.Instant;\n");
        }
        if (fields.stream().anyMatch(field -> field.type() == FieldType.DATE)) {
            imports.append("import java.time.LocalDate;\n");
        }
        if (fields.stream()
                .anyMatch(
                        field ->
                                field.type() == FieldType.UUID
                                        || field.type() == FieldType.REFERENCE)) {
            imports.append("import java.util.UUID;\n");
        }
        if (includeLists && fields.stream().anyMatch(field -> field.type() == FieldType.LIST)) {
            imports.append("import java.util.List;\n");
        }
        return imports.toString().stripTrailing();
    }

    private String persistenceMapper(Layout layout, String name, List<FieldDefinition> fields) {
        var toDomain = new ArrayList<String>();
        toDomain.add(
                "new " + name + "Id(entity.get" + capitalize(fields.getFirst().name()) + "())");
        fields.stream()
                .skip(1)
                .forEach(field -> toDomain.add("entity.get" + capitalize(field.name()) + "()"));
        var toEntity = new ArrayList<String>();
        toEntity.add("aggregate.id().value()");
        fields.stream().skip(1).forEach(field -> toEntity.add("aggregate." + field.name() + "()"));
        return """
                package %s;

                import %s.%s;
                import %s.%sId;
                import org.springframework.stereotype.Component;

                @Component
                class %sPersistenceMapper {
                    %s toDomain(%sJpaEntity entity) {
                        return new %s(
                                %s);
                    }

                    %sJpaEntity toEntity(%s aggregate) {
                        return new %sJpaEntity(
                                %s);
                    }
                }
                """
                .formatted(
                        layout.persistencePackage(),
                        layout.domainPackage(),
                        name,
                        layout.domainPackage(),
                        name,
                        name,
                        name,
                        name,
                        name,
                        String.join(",\n                ", toDomain),
                        name,
                        name,
                        name,
                        String.join(",\n                ", toEntity));
    }

    private Map<Path, String> entityMigration(String name, List<FieldDefinition> fields) {
        var columns = new ArrayList<String>();
        var constraints = new ArrayList<String>();
        for (int index = 0; index < fields.size(); index++) {
            FieldDefinition field = fields.get(index);
            String definition = "  " + snake(field.name()) + " " + sqlType(field);
            if (index == 0) definition += " PRIMARY KEY";
            if (field.required()) definition += " NOT NULL";
            if (field.unique()) definition += " UNIQUE";
            columns.add(definition);
        }
        columns.add("  created_at ${timestampType} NOT NULL DEFAULT CURRENT_TIMESTAMP");
        columns.add("  updated_at ${timestampType} NOT NULL DEFAULT CURRENT_TIMESTAMP");
        columns.add("  version BIGINT NOT NULL DEFAULT 0");
        String version =
                String.valueOf(
                        10000 + Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), 89999));
        return Map.of(
                Path.of(
                        "src/main/resources/db/migration",
                        "V" + version + "__create_" + snake(name) + ".sql"),
                """
                CREATE TABLE %s (
                %s
                );
                %s
                """
                        .formatted(
                                snake(name),
                                String.join(",\n", columns),
                                String.join("\n", constraints)));
    }

    private Map<Path, String> valueObject(
            ProjectConfiguration configuration, String name, String module, String subtype) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String body;
        if (name.equals("Money") || "money".equalsIgnoreCase(subtype)) {
            body =
                    """
                    package %s;

                    import java.math.BigDecimal;
                    import java.util.Currency;
                    import java.util.Objects;

                    public record Money(BigDecimal amount, Currency currency) {
                        public Money {
                            Objects.requireNonNull(amount, "amount");
                            Objects.requireNonNull(currency, "currency");
                        }
                    }
                    """
                            .formatted(layout.domainPackage());
        } else if (name.equals("EmailAddress") || "email".equalsIgnoreCase(subtype)) {
            body =
                    """
                    package %s;

                    import java.util.regex.Pattern;

                    public record EmailAddress(String value) {
                        private static final Pattern VALID = Pattern.compile("^[^@\\\\s]+@[^@\\\\s]+\\\\.[^@\\\\s]+$");
                        public EmailAddress {
                            if (value == null || !VALID.matcher(value).matches()) {
                                throw new IllegalArgumentException("invalid email address");
                            }
                        }
                    }
                    """
                            .formatted(layout.domainPackage());
        } else if (name.equals("PhoneNumber") || "phone".equalsIgnoreCase(subtype)) {
            body =
                    """
                    package %s;

                    import java.util.regex.Pattern;

                    public record PhoneNumber(String value) {
                        private static final Pattern E164 = Pattern.compile("^\\\\+[1-9][0-9]{7,14}$");
                        public PhoneNumber {
                            if (value == null || !E164.matcher(value).matches()) {
                                throw new IllegalArgumentException("phone number must use E.164 format");
                            }
                        }
                    }
                    """
                            .formatted(layout.domainPackage());
        } else if (name.equals("Percentage") || "percentage".equalsIgnoreCase(subtype)) {
            body =
                    """
                    package %s;

                    import java.math.BigDecimal;
                    import java.util.Objects;

                    public record Percentage(BigDecimal value) {
                        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
                        public Percentage {
                            Objects.requireNonNull(value, "value");
                            if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
                                throw new IllegalArgumentException("percentage must be between 0 and 100");
                            }
                        }
                    }
                    """
                            .formatted(layout.domainPackage());
        } else if (name.endsWith("Id") || "id".equalsIgnoreCase(subtype)) {
            body =
                    """
                    package %s;

                    import java.util.Objects;
                    import java.util.UUID;

                    public record %s(UUID value) {
                        public %s {
                            Objects.requireNonNull(value, "value");
                        }
                        public static %s random() {
                            return new %s(UUID.randomUUID());
                        }
                    }
                    """
                            .formatted(layout.domainPackage(), name, name, name, name);
        } else {
            body =
                    """
                    package %s;

                    public record %s(String value) {
                        public %s {
                            if (value == null || value.isBlank()) {
                                throw new IllegalArgumentException("value must not be blank");
                            }
                        }
                    }
                    """
                            .formatted(layout.domainPackage(), name, name);
        }
        return Map.of(source(layout.domainPackage(), name + ".java"), body);
    }

    private Map<Path, String> useCase(
            ProjectConfiguration configuration, String name, String module, String subtype) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        boolean query = "query".equalsIgnoreCase(subtype);
        String portPackage = layout.applicationPackage() + (query ? ".query" : ".command");
        String verb = decapitalize(name);
        return files(
                source(portPackage, name + "UseCase.java"),
                """
                package %s;

                public interface %sUseCase {
                    %sResult execute(%sCommand command);
                }
                """
                        .formatted(portPackage, name, name, name),
                source(portPackage, name + "Command.java"),
                """
                package %s;

                public record %sCommand(String requestId) {
                    public %sCommand {
                        if (requestId == null || requestId.isBlank()) {
                            throw new IllegalArgumentException("requestId must not be blank");
                        }
                    }
                }
                """
                        .formatted(portPackage, name, name),
                source(portPackage, name + "Result.java"),
                """
                package %s;

                public record %sResult(String requestId, String status) {}
                """
                        .formatted(portPackage, name),
                source(layout.applicationPackage() + ".service", name + "Service.java"),
                """
                package %s.service;

                import %s.%sCommand;
                import %s.%sResult;
                import %s.%sUseCase;
                import org.springframework.stereotype.Service;

                @Service
                public class %sService implements %sUseCase {
                    @Override
                    public %sResult execute(%sCommand command) {
                        return new %sResult(command.requestId(), "%s");
                    }
                }
                """
                        .formatted(
                                layout.applicationPackage(),
                                portPackage,
                                name,
                                portPackage,
                                name,
                                portPackage,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                query ? "FOUND" : "ACCEPTED"),
                testSource(portPackage, name + "CommandTest.java"),
                """
                package %s;

                import static org.junit.jupiter.api.Assertions.assertThrows;
                import org.junit.jupiter.api.Test;

                class %sCommandTest {
                    @Test void rejectsBlankRequestId() {
                        assertThrows(IllegalArgumentException.class, () -> new %sCommand(" "));
                    }
                }
                """
                        .formatted(portPackage, name, name));
    }

    private Map<Path, String> controller(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String path = "/" + snake(name).replace('_', '-');
        return files(
                source(layout.webPackage(), name + "Api.java"),
                """
                package %s;

                import org.springframework.http.ResponseEntity;

                public interface %sApi {
                    ResponseEntity<%sResponse> handle(%sRequest request);
                    record %sRequest(String requestId) {}
                    record %sResponse(String requestId, String status) {}
                }
                """
                        .formatted(layout.webPackage(), name, name, name, name, name),
                source(layout.webPackage(), name + "Handler.java"),
                """
                package %s;

                public interface %sHandler {
                    String handle(String requestId);
                }
                """
                        .formatted(layout.webPackage(), name),
                source(layout.webPackage(), name + "Controller.java"),
                """
                package %s;

                import jakarta.validation.Valid;
                import jakarta.validation.constraints.NotBlank;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api/v1%s")
                public class %sController implements %sApi {
                    private final %sHandler handler;

                    public %sController(%sHandler handler) {
                        this.handler = handler;
                    }

                    @PostMapping
                    public ResponseEntity<%sResponse> handle(@Valid @RequestBody %sRequest request) {
                        return ResponseEntity.ok(new %sResponse(request.requestId(), handler.handle(request.requestId())));
                    }
                }
                """
                        .formatted(
                                layout.webPackage(),
                                path,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
    }

    private Map<Path, String> repository(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        return Map.of(
                source(layout.repositoryPackage(), name + "Repository.java"),
                """
                package %s;

                import %s.%s;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;

                public interface %sRepository {
                    %s save(%s value);
                    Optional<%s> findById(UUID id);
                    List<%s> findAll();
                }
                """
                        .formatted(
                                layout.repositoryPackage(),
                                layout.domainPackage(),
                                name,
                                name,
                                name,
                                name,
                                name,
                                name));
    }

    private Map<Path, String> service(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String packageName = layout.applicationPackage() + ".service";
        return Map.of(
                source(packageName, name + "Service.java"),
                """
                package %s;

                import org.springframework.stereotype.Service;

                @Service
                public class %sService {
                    public String execute(String requestId) {
                        if (requestId == null || requestId.isBlank()) {
                            throw new IllegalArgumentException("requestId must not be blank");
                        }
                        return requestId;
                    }
                }
                """
                        .formatted(packageName, name));
    }

    private Map<Path, String> migration(
            ProjectConfiguration configuration, String name, List<FieldDefinition> fields) {
        if (DatabaseProfile.from(configuration.features().database()).mongodb()) {
            throw new IllegalArgumentException(
                    "migration is unavailable for MongoDB projects because migrations are disabled");
        }
        String version =
                String.valueOf(
                        10000 + Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), 89999));
        String columns =
                fields.isEmpty()
                        ? "  id ${uuidType} PRIMARY KEY,\n"
                                + "  created_at ${timestampType} NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        : fields.stream()
                                .map(
                                        field ->
                                                "  "
                                                        + snake(field.name())
                                                        + " "
                                                        + sqlType(field)
                                                        + (field.required() ? " NOT NULL" : ""))
                                .collect(java.util.stream.Collectors.joining(",\n"));
        return Map.of(
                Path.of(
                        "src/main/resources/db/migration",
                        "V" + version + "__" + snake(name) + ".sql"),
                """
                CREATE TABLE %s (
                %s
                );
                """
                        .formatted(snake(name), columns));
    }

    private Map<Path, String> event(
            ProjectConfiguration configuration,
            String name,
            String module,
            List<FieldDefinition> fields) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String packageName = layout.domainPackage() + ".event";
        var components = new ArrayList<String>();
        components.add("UUID eventId");
        components.add("String aggregateId");
        components.add("String correlationId");
        components.add("Instant occurredAt");
        fields.forEach(field -> components.add(javaType(field, name, true) + " " + field.name()));
        return Map.of(
                source(packageName, name + ".java"),
                """
                package %s;

                import java.math.BigDecimal;
                import java.time.Instant;
                import java.util.UUID;

                public record %s(
                        %s) {
                    public %s {
                        if (eventId == null || occurredAt == null) throw new IllegalArgumentException("event metadata is required");
                    }
                }
                """
                        .formatted(
                                packageName, name, String.join(",\n        ", components), name));
    }

    private Map<Path, String> producer(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String packageName = layout.applicationPackage() + ".port.out";
        return Map.of(
                source(packageName, name + "Producer.java"),
                """
                package %s;

                import java.util.concurrent.CompletionStage;

                public interface %sProducer {
                    CompletionStage<Void> publish(String key, Object event);
                }
                """
                        .formatted(packageName, name));
    }

    private Map<Path, String> consumer(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String packageName = layout.applicationPackage() + ".port.in";
        return Map.of(
                source(packageName, name + "Consumer.java"),
                """
                package %s;

                public interface %sConsumer {
                    void consume(String eventId, String payload);
                }
                """
                        .formatted(packageName, name));
    }

    private Map<Path, String> scheduler(
            ProjectConfiguration configuration, String name, String module) {
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String packageName = layout.applicationPackage() + ".job";
        return files(
                source(packageName, name + "Job.java"),
                """
                package %s;

                @FunctionalInterface
                public interface %sJob {
                    void execute();
                }
                """
                        .formatted(packageName, name),
                source(packageName, name + "JobHandler.java"),
                """
                package %s;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.stereotype.Component;

                @Component
                public class %sJobHandler implements %sJob {
                    private static final Logger LOG = LoggerFactory.getLogger(%sJobHandler.class);

                    @Override
                    public void execute() {
                        LOG.info("scheduled_job_completed job=%s");
                    }
                }
                """
                        .formatted(packageName, name, name, name, decapitalize(name)),
                source(packageName, name + "Scheduler.java"),
                """
                package %s;

                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                public class %sScheduler {
                    private final %sJob job;

                    public %sScheduler(%sJob job) {
                        this.job = job;
                    }

                    @Scheduled(fixedDelayString = "${backsmith.jobs.%s.delay:60000}")
                    public void run() {
                        job.execute();
                    }
                }
                """
                        .formatted(packageName, name, name, name, name, decapitalize(name)));
    }

    private Map<Path, String> integration(
            ProjectConfiguration configuration, String name, String module, String subtype) {
        if (subtype != null && !subtype.equalsIgnoreCase("rest")) {
            throw new IllegalArgumentException("only REST integrations are supported");
        }
        Layout layout =
                Layout.forArchitecture(
                        configuration.project().basePackage(),
                        module,
                        configuration.architecture().type());
        String portPackage = layout.applicationPackage() + ".port.out";
        String adapterPackage = layout.persistencePackage().replace(".persistence", ".integration");
        var resilienceImports = new StringBuilder();
        var resilienceAnnotations = new StringBuilder();
        if (configuration.resilience().circuitBreaker()) {
            resilienceImports.append(
                    "import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;\n");
            resilienceAnnotations.append("    @CircuitBreaker(name = \"external\")\n");
        }
        if (configuration.resilience().rateLimiter()) {
            resilienceImports.append(
                    "import io.github.resilience4j.ratelimiter.annotation.RateLimiter;\n");
            resilienceAnnotations.append("    @RateLimiter(name = \"external\")\n");
        }
        if (configuration.resilience().bulkhead()) {
            resilienceImports.append(
                    "import io.github.resilience4j.bulkhead.annotation.Bulkhead;\n");
            resilienceAnnotations.append("    @Bulkhead(name = \"external\")\n");
        }
        return files(
                source(portPackage, name + "Client.java"),
                """
                package %s;

                public interface %sClient {
                    %sResponse check(%sRequest request);
                    record %sRequest(String reference) {}
                    record %sResponse(String status) {}
                }
                """
                        .formatted(portPackage, name, name, name, name, name),
                source(adapterPackage, "IntegrationAuthentication.java"),
                """
                package %s;

                import java.util.Optional;

                @FunctionalInterface
                public interface IntegrationAuthentication {
                    Optional<String> authorizationHeader();
                }
                """
                        .formatted(adapterPackage),
                source(adapterPackage, name + "RestClient.java"),
                """
                package %s;

                import %s.%sClient;
                import io.micrometer.core.instrument.MeterRegistry;
                import java.time.Duration;
                import java.util.Optional;
                %s
                import org.springframework.beans.factory.ObjectProvider;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.http.client.SimpleClientHttpRequestFactory;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestClient;
                import org.springframework.web.client.RestClientResponseException;

                @Component
                public class %sRestClient implements %sClient {
                    private final RestClient client;
                    private final MeterRegistry metrics;

                    public %sRestClient(
                            RestClient.Builder builder,
                            MeterRegistry metrics,
                            ObjectProvider<IntegrationAuthentication> authentication,
                            @Value("${integrations.%s.base-url:http://localhost:8081}") String baseUrl,
                            @Value("${integrations.%s.connect-timeout:PT2S}") Duration connectTimeout,
                            @Value("${integrations.%s.read-timeout:PT3S}") Duration readTimeout) {
                        var requests = new SimpleClientHttpRequestFactory();
                        requests.setConnectTimeout(connectTimeout);
                        requests.setReadTimeout(readTimeout);
                        var configured = builder.baseUrl(baseUrl).requestFactory(requests);
                        authentication.getIfAvailable(() -> Optional::empty)
                                .authorizationHeader()
                                .ifPresent(value -> configured.defaultHeader("Authorization", value));
                        this.client = configured.build();
                        this.metrics = metrics;
                    }

                %s
                    @Override
                    public %sResponse check(%sRequest request) {
                        return metrics.timer("backsmith.integration.request", "integration", "%s")
                                .record(() -> {
                                    try {
                                        return client.post().uri("/check").body(request)
                                                .retrieve().body(%sResponse.class);
                                    } catch (RestClientResponseException failure) {
                                        throw new IntegrationException(failure.getStatusCode().value());
                                    }
                                });
                    }

                    public static final class IntegrationException extends RuntimeException {
                        private final int status;
                        IntegrationException(int status) {
                            super("external integration failed");
                            this.status = status;
                        }
                        public int status() { return status; }
                    }
                }
                """
                        .formatted(
                                adapterPackage,
                                portPackage,
                                name,
                                resilienceImports,
                                name,
                                name,
                                name,
                                decapitalize(name),
                                decapitalize(name),
                                decapitalize(name),
                                resilienceAnnotations,
                                name,
                                name,
                                decapitalize(name),
                                name));
    }

    private Map<Path, String> docker(ProjectConfiguration configuration) {
        return files(
                Path.of("Dockerfile"),
                """
                FROM eclipse-temurin:21-jdk AS build
                WORKDIR /workspace
                COPY . .
                RUN ./mvnw -B -DskipTests package
                FROM eclipse-temurin:21-jre
                RUN useradd --system --uid 10001 app
                USER 10001
                COPY --from=build /workspace/target/*.jar /app.jar
                EXPOSE 8080
                ENTRYPOINT ["java","-jar","/app.jar"]
                """,
                Path.of(".dockerignore"),
                "target/\n.git/\n.env\n",
                Path.of("compose.yaml"),
                """
                services:
                  postgres:
                    image: postgres:17-alpine
                    environment:
                      POSTGRES_DB: %s
                      POSTGRES_USER: postgres
                      POSTGRES_PASSWORD: postgres
                    healthcheck:
                      test: ["CMD-SHELL", "pg_isready -U postgres -d %s"]
                      interval: 5s
                      timeout: 3s
                      retries: 10
                """
                        .formatted(
                                configuration.project().artifactId(),
                                configuration.project().artifactId()));
    }

    private Map<Path, String> ci() {
        return Map.of(
                Path.of(".github/workflows/build.yml"),
                """
                name: build
                on: [push, pull_request]
                permissions:
                  contents: read
                jobs:
                  verify:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
                      - uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95
                        with:
                          distribution: temurin
                          java-version: "21"
                          cache: maven
                      - run: ./mvnw -B verify
                """);
    }

    private Map<Path, String> docs(ProjectConfiguration configuration) {
        return Map.of(
                Path.of("docs", "generated-project.md"),
                """
                # %s generated project

                Architecture: `%s`

                ## Verify

                ```shell
                ./mvnw verify
                docker compose config
                ```

                Review generated security, infrastructure, and data-retention settings before production use.
                """
                        .formatted(
                                configuration.project().name(),
                                configuration.architecture().type()));
    }

    private void addEnums(
            Map<Path, String> generated,
            String packageName,
            String entityName,
            List<FieldDefinition> fields) {
        fields.stream()
                .filter(field -> field.type() == FieldType.ENUM)
                .forEach(
                        field -> {
                            String enumName = entityName + capitalize(field.name());
                            generated.put(
                                    source(packageName, enumName + ".java"),
                                    """
                    package %s;

                    public enum %s {
                        %s
                    }
                    """
                                            .formatted(
                                                    packageName,
                                                    enumName,
                                                    String.join(",\n    ", field.enumValues())));
                        });
    }

    private List<FieldDefinition> withIdentifier(List<FieldDefinition> supplied) {
        if (supplied.stream().anyMatch(field -> field.name().equals("id"))) {
            var ordered = new ArrayList<FieldDefinition>();
            supplied.stream()
                    .filter(field -> field.name().equals("id"))
                    .findFirst()
                    .ifPresent(ordered::add);
            supplied.stream().filter(field -> !field.name().equals("id")).forEach(ordered::add);
            return List.copyOf(ordered);
        }
        var fields = new ArrayList<FieldDefinition>();
        fields.add(FieldDefinition.parse("id:uuid:required:generated"));
        fields.addAll(supplied);
        return List.copyOf(fields);
    }

    private String javaType(FieldDefinition field, String entityName, boolean domain) {
        return switch (field.type()) {
            case STRING, TEXT, EMAIL, JSON -> "String";
            case INTEGER -> "Integer";
            case LONG -> "Long";
            case DECIMAL -> "BigDecimal";
            case BOOLEAN -> "Boolean";
            case UUID, REFERENCE -> "UUID";
            case DATE -> "LocalDate";
            case INSTANT -> "Instant";
            case BINARY -> "byte[]";
            case LIST -> domain ? "List<String>" : "String";
            case ENUM -> entityName.replace("JpaEntity", "") + capitalize(field.name());
        };
    }

    private String sqlType(FieldDefinition field) {
        return switch (field.type()) {
            case STRING, EMAIL ->
                    "VARCHAR("
                            + field.options()
                                    .getOrDefault(
                                            "max", field.options().getOrDefault("length", "255"))
                            + ")";
            case TEXT -> "${textType}";
            case INTEGER -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL ->
                    "NUMERIC("
                            + field.options().get("precision")
                            + ","
                            + field.options().get("scale")
                            + ")";
            case BOOLEAN -> "${booleanType}";
            case UUID, REFERENCE -> "${uuidType}";
            case DATE -> "DATE";
            case INSTANT -> "${timestampType}";
            case ENUM -> "VARCHAR(64)";
            case JSON -> "${jsonType}";
            case BINARY -> "${binaryType}";
            case LIST -> "${jsonType}";
        };
    }

    private boolean isPrimitive(FieldDefinition field) {
        return false;
    }

    private String importFor(String type) {
        return switch (simpleType(type)) {
            case "UUID" -> "java.util.UUID";
            case "Long" -> "java.lang.Long";
            case "Integer" -> "java.lang.Integer";
            case "String" -> "java.lang.String";
            default -> "java.lang.Object";
        };
    }

    private String simpleType(String type) {
        return type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
    }

    private String entityTypeTest(String packageName, String name) {
        return """
                package %s;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import org.junit.jupiter.api.Test;

                class %sTest {
                    @Test void exposesItsDomainName() {
                        assertEquals("%s", %s.class.getSimpleName());
                    }
                }
                """
                .formatted(packageName, name, name, name);
    }

    private Map<Path, String> files(Object... values) {
        var files = new LinkedHashMap<Path, String>();
        for (int index = 0; index < values.length; index += 2) {
            files.put((Path) values[index], (String) values[index + 1]);
        }
        return Map.copyOf(files);
    }

    private Path source(String packageName, String fileName) {
        return Path.of("src/main/java", packageName.replace('.', '/'), fileName);
    }

    private Path testSource(String packageName, String fileName) {
        return Path.of("src/test/java", packageName.replace('.', '/'), fileName);
    }

    private String requiredName(String name, String generator) {
        if (name == null) throw new IllegalArgumentException(generator + " requires a name");
        return name;
    }

    private String safeType(String value) {
        if (!value.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("name must be an UpperCamelCase Java type");
        }
        return value;
    }

    private String safePackage(String value) {
        if (!value.matches("[a-z][a-z0-9]*")) {
            throw new IllegalArgumentException("module must be a lowercase Java identifier");
        }
        return value;
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String snake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
    }

    private record Layout(
            String domainPackage,
            String repositoryPackage,
            String applicationPackage,
            String persistencePackage,
            String webPackage) {
        static Layout forArchitecture(
                String basePackage, String module, Architecture architecture) {
            String root = basePackage + ".modules." + module;
            return switch (architecture) {
                case LAYERED ->
                        new Layout(
                                root + ".entity",
                                root + ".repository",
                                root + ".service",
                                root + ".repository",
                                root + ".controller");
                case CLEAN ->
                        new Layout(
                                root + ".domain",
                                root + ".application.port.out",
                                root + ".application",
                                root + ".infrastructure.persistence",
                                root + ".interfaceadapters.web");
                case ONION ->
                        new Layout(
                                root + ".domain",
                                root + ".application.port.out",
                                root + ".application",
                                root + ".infrastructure.persistence",
                                root + ".api");
                case CQRS ->
                        new Layout(
                                root + ".domain",
                                root + ".application.port.out",
                                root + ".application",
                                root + ".infrastructure.persistence",
                                root + ".api");
                case MODULAR_MONOLITH ->
                        new Layout(
                                root + ".domain",
                                root + ".application.port.out",
                                root + ".application",
                                root + ".infrastructure.persistence",
                                root + ".api");
                case HEXAGONAL, MICROSERVICE ->
                        new Layout(
                                root + ".domain.model",
                                root + ".application.port.out",
                                root + ".application",
                                root + ".adapter.out.persistence",
                                root + ".adapter.in.web");
            };
        }
    }
}

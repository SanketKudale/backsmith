package io.backsmith.adapter.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpringOpenApiGenerator {
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "post", "put", "patch", "delete", "head", "options");
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public Map<Path, String> generate(
            ProjectConfiguration configuration, Path project, Path contract, String module)
            throws IOException {
        Path resolved = contract.isAbsolute() ? contract : project.resolve(contract);
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("OpenAPI contract not found: " + resolved);
        }
        return generateContract(
                configuration,
                mapper.readTree(resolved.toFile()),
                module,
                typeName(stripExtension(resolved.getFileName().toString())));
    }

    public Map<Path, String> generateDirectory(
            ProjectConfiguration configuration, Path project, Path directory, String module)
            throws IOException {
        Path resolved = directory.isAbsolute() ? directory : project.resolve(directory);
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("OpenAPI directory not found: " + resolved);
        }
        var files = new LinkedHashMap<Path, String>();
        try (var contracts = Files.list(resolved)) {
            for (Path contract :
                    contracts
                            .filter(Files::isRegularFile)
                            .filter(
                                    path -> {
                                        String name =
                                                path.getFileName()
                                                        .toString()
                                                        .toLowerCase(Locale.ROOT);
                                        return name.endsWith(".yaml")
                                                || name.endsWith(".yml")
                                                || name.endsWith(".json");
                                    })
                            .sorted()
                            .toList()) {
                for (var entry : generate(configuration, project, contract, module).entrySet()) {
                    if (files.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                        throw new IllegalArgumentException(
                                "contracts generate the same file: " + entry.getKey());
                    }
                }
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException(
                    "no OpenAPI YAML or JSON contracts found in " + resolved);
        }
        return Map.copyOf(files);
    }

    private Map<Path, String> generateContract(
            ProjectConfiguration configuration, JsonNode root, String module, String contractName) {
        String version = root.path("openapi").asText();
        if (!version.startsWith("3.1")) {
            throw new IllegalArgumentException(
                    "openapi: Backsmith requires an OpenAPI 3.1 contract; found '" + version + "'");
        }
        JsonNode paths = root.path("paths");
        if (!paths.isObject() || paths.size() == 0) {
            throw new IllegalArgumentException("paths: at least one API operation is required");
        }
        String webPackage = webPackage(configuration, module);
        String dtoPackage = webPackage + ".dto";
        var files = new LinkedHashMap<Path, String>();
        JsonNode schemas = root.path("components").path("schemas");
        if (schemas.isObject()) {
            schemas.properties()
                    .forEach(
                            schema ->
                                    files.put(
                                            source(dtoPackage, typeName(schema.getKey()) + ".java"),
                                            dtoSource(
                                                    dtoPackage,
                                                    typeName(schema.getKey()),
                                                    schema.getValue())));
        }
        List<Operation> operations = operations(paths);
        String apiName = contractName.endsWith("Api") ? contractName : contractName + "Api";
        files.put(
                source(webPackage, apiName + ".java"),
                apiSource(webPackage, dtoPackage, apiName, operations));
        files.put(
                source(webPackage, contractName + "Handler.java"),
                handlerSource(webPackage, dtoPackage, contractName, operations));
        files.put(
                source(webPackage, contractName + "Controller.java"),
                controllerSource(webPackage, dtoPackage, contractName, apiName, operations));
        files.put(
                testSource(webPackage, apiName + "ContractTest.java"),
                """
                package %s;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import org.junit.jupiter.api.Test;

                class %sContractTest {
                    @Test void exposesEveryContractOperation() {
                        assertEquals(%d, %s.class.getDeclaredMethods().length);
                    }
                }
                """
                        .formatted(webPackage, apiName, operations.size(), apiName));
        return Map.copyOf(files);
    }

    private List<Operation> operations(JsonNode paths) {
        var operations = new ArrayList<Operation>();
        paths.properties()
                .forEach(
                        path ->
                                path.getValue()
                                        .properties()
                                        .forEach(
                                                method -> {
                                                    if (!HTTP_METHODS.contains(
                                                            method.getKey()
                                                                    .toLowerCase(Locale.ROOT)))
                                                        return;
                                                    JsonNode operation = method.getValue();
                                                    String operationId =
                                                            operation.path("operationId").asText();
                                                    if (operationId.isBlank()) {
                                                        operationId =
                                                                lowerCamel(
                                                                        method.getKey()
                                                                                + " "
                                                                                + path.getKey());
                                                    }
                                                    String requestType =
                                                            referencedType(
                                                                    operation
                                                                            .path("requestBody")
                                                                            .path("content")
                                                                            .path(
                                                                                    "application/json")
                                                                            .path("schema"));
                                                    String responseType =
                                                            responseType(
                                                                    operation.path("responses"));
                                                    operations.add(
                                                            new Operation(
                                                                    operationId,
                                                                    method.getKey()
                                                                            .toUpperCase(
                                                                                    Locale.ROOT),
                                                                    path.getKey(),
                                                                    requestType,
                                                                    responseType));
                                                }));
        operations.sort(Comparator.comparing(Operation::operationId));
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("paths: no supported HTTP operations were found");
        }
        return List.copyOf(operations);
    }

    private String dtoSource(String packageName, String name, JsonNode schema) {
        JsonNode properties = schema.path("properties");
        Set<String> required = new java.util.LinkedHashSet<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        var components = new ArrayList<String>();
        var imports = new java.util.TreeSet<String>();
        if (properties.isObject()) {
            properties
                    .properties()
                    .forEach(
                            property -> {
                                String annotations =
                                        validationAnnotations(
                                                property.getValue(),
                                                required.contains(property.getKey()));
                                String type = schemaType(property.getValue(), imports);
                                components.add(
                                        (annotations.isBlank() ? "" : annotations + " ")
                                                + type
                                                + " "
                                                + property.getKey());
                            });
        }
        if (components.isEmpty()) {
            components.add("String value");
        }
        String importBlock =
                imports.stream()
                        .map(value -> "import " + value + ";")
                        .collect(java.util.stream.Collectors.joining("\n"));
        return """
                package %s;

                import jakarta.validation.constraints.*;
                %s

                public record %s(
                        %s) {}
                """
                .formatted(packageName, importBlock, name, String.join(",\n        ", components));
    }

    private String apiSource(
            String packageName, String dtoPackage, String apiName, List<Operation> operations) {
        var methods = new StringBuilder();
        for (Operation operation : operations) {
            String mapping =
                    switch (operation.method()) {
                        case "GET" -> "@GetMapping";
                        case "POST" -> "@PostMapping";
                        case "PUT" -> "@PutMapping";
                        case "PATCH" -> "@PatchMapping";
                        case "DELETE" -> "@DeleteMapping";
                        default -> "@RequestMapping";
                    };
            methods.append("    ")
                    .append(mapping)
                    .append("(\"")
                    .append(operation.path())
                    .append("\")\n")
                    .append("    ResponseEntity<")
                    .append(operation.responseType())
                    .append("> ")
                    .append(operation.operationId())
                    .append("(");
            if (operation.requestType() != null) {
                methods.append("@Valid @RequestBody ")
                        .append(operation.requestType())
                        .append(" request");
            }
            methods.append(");\n\n");
        }
        return """
                package %s;

                import %s.*;
                import jakarta.validation.Valid;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                public interface %s {
                %s}
                """
                .formatted(packageName, dtoPackage, apiName, methods);
    }

    private String handlerSource(
            String packageName,
            String dtoPackage,
            String contractName,
            List<Operation> operations) {
        var methods = new StringBuilder();
        for (Operation operation : operations) {
            methods.append("    ")
                    .append(operation.responseType())
                    .append(' ')
                    .append(operation.operationId())
                    .append('(');
            if (operation.requestType() != null) {
                methods.append(operation.requestType()).append(" request");
            }
            methods.append(");\n");
        }
        return """
                package %s;

                import %s.*;

                /** Application boundary implemented outside the web adapter. */
                public interface %sHandler {
                %s}
                """
                .formatted(packageName, dtoPackage, contractName, methods);
    }

    private String controllerSource(
            String packageName,
            String dtoPackage,
            String contractName,
            String apiName,
            List<Operation> operations) {
        var methods = new StringBuilder();
        for (Operation operation : operations) {
            methods.append("    @Override\n    public ResponseEntity<")
                    .append(operation.responseType())
                    .append("> ")
                    .append(operation.operationId())
                    .append('(');
            if (operation.requestType() != null) {
                methods.append(operation.requestType()).append(" request");
            }
            methods.append(") {\n        return ResponseEntity.ok(handler.")
                    .append(operation.operationId())
                    .append('(');
            if (operation.requestType() != null) methods.append("request");
            methods.append("));\n    }\n\n");
        }
        return """
                package %s;

                import %s.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class %sController implements %s {
                    private final %sHandler handler;

                    public %sController(%sHandler handler) {
                        this.handler = handler;
                    }

                %s}
                """
                .formatted(
                        packageName,
                        dtoPackage,
                        contractName,
                        apiName,
                        contractName,
                        contractName,
                        contractName,
                        methods);
    }

    private String validationAnnotations(JsonNode schema, boolean required) {
        var annotations = new ArrayList<String>();
        if (required) {
            annotations.add(
                    schema.path("type").asText().equals("string") ? "@NotBlank" : "@NotNull");
        }
        if ("email".equals(schema.path("format").asText())) annotations.add("@Email");
        if (schema.has("maxLength"))
            annotations.add("@Size(max = " + schema.path("maxLength").asInt() + ")");
        if (schema.has("minimum"))
            annotations.add("@DecimalMin(\"" + schema.path("minimum").asText() + "\")");
        return String.join(" ", annotations);
    }

    private String schemaType(JsonNode schema, Set<String> imports) {
        if (schema.has("$ref")) return referencedType(schema);
        String type = schema.path("type").asText("object");
        String format = schema.path("format").asText();
        return switch (type) {
            case "string" ->
                    switch (format) {
                        case "uuid" -> {
                            imports.add("java.util.UUID");
                            yield "UUID";
                        }
                        case "date" -> {
                            imports.add("java.time.LocalDate");
                            yield "LocalDate";
                        }
                        case "date-time" -> {
                            imports.add("java.time.Instant");
                            yield "Instant";
                        }
                        default -> "String";
                    };
            case "integer" -> "int64".equals(format) ? "Long" : "Integer";
            case "number" -> {
                imports.add("java.math.BigDecimal");
                yield "BigDecimal";
            }
            case "boolean" -> "Boolean";
            case "array" -> {
                imports.add("java.util.List");
                yield "List<" + schemaType(schema.path("items"), imports) + ">";
            }
            default -> "Object";
        };
    }

    private String responseType(JsonNode responses) {
        if (!responses.isObject()) return "Void";
        Iterator<Map.Entry<String, JsonNode>> iterator = responses.properties().iterator();
        while (iterator.hasNext()) {
            var response = iterator.next();
            if (response.getKey().startsWith("2")) {
                String referenced =
                        referencedType(
                                response.getValue()
                                        .path("content")
                                        .path("application/json")
                                        .path("schema"));
                return referenced == null ? "Void" : referenced;
            }
        }
        return "Void";
    }

    private String referencedType(JsonNode schema) {
        String reference = schema.path("$ref").asText();
        if (reference.isBlank()) return null;
        return typeName(reference.substring(reference.lastIndexOf('/') + 1));
    }

    private String webPackage(ProjectConfiguration configuration, String module) {
        String root = configuration.project().basePackage() + ".modules." + module;
        Architecture architecture = configuration.architecture().type();
        return switch (architecture) {
            case LAYERED -> root + ".controller";
            case CLEAN -> root + ".interfaceadapters.web";
            case ONION, CQRS, MODULAR_MONOLITH -> root + ".api";
            case HEXAGONAL, MICROSERVICE -> root + ".adapter.in.web";
        };
    }

    private Path source(String packageName, String fileName) {
        return Path.of("src/main/java", packageName.replace('.', '/'), fileName);
    }

    private Path testSource(String packageName, String fileName) {
        return Path.of("src/test/java", packageName.replace('.', '/'), fileName);
    }

    private String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }

    private String typeName(String value) {
        String[] words = value.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        var result = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) {
            throw new IllegalArgumentException("cannot derive a Java type from: " + value);
        }
        return result.toString();
    }

    private String lowerCamel(String value) {
        String type = typeName(value);
        return Character.toLowerCase(type.charAt(0)) + type.substring(1);
    }

    private record Operation(
            String operationId,
            String method,
            String path,
            String requestType,
            String responseType) {}
}

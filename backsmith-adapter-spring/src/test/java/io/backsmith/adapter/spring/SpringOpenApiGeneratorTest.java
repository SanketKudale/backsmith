package io.backsmith.adapter.spring;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringOpenApiGeneratorTest {
    @TempDir Path project;

    @Test
    void generatesDtosApiAndControllerFromOpenApi31() throws Exception {
        Path contract = project.resolve("payments.yaml");
        Files.writeString(
                contract,
                """
                openapi: 3.1.0
                info:
                  title: Payments
                  version: 1.0.0
                paths:
                  /api/v1/payments:
                    post:
                      operationId: createPayment
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              $ref: '#/components/schemas/CreatePaymentRequest'
                      responses:
                        '200':
                          description: Created
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/PaymentResponse'
                components:
                  schemas:
                    CreatePaymentRequest:
                      type: object
                      required: [amount]
                      properties:
                        amount: {type: number}
                    PaymentResponse:
                      type: object
                      properties:
                        id: {type: string, format: uuid}
                """);

        var generated =
                new SpringOpenApiGenerator()
                        .generate(
                                ProjectConfiguration.defaults("payments", Architecture.HEXAGONAL),
                                project,
                                contract,
                                "payment");

        assertTrue(generated.keySet().stream().anyMatch(path -> path.endsWith("PaymentsApi.java")));
        assertTrue(
                generated.keySet().stream()
                        .anyMatch(path -> path.endsWith("CreatePaymentRequest.java")));
        assertTrue(
                generated.values().stream().anyMatch(source -> source.contains("createPayment")));
    }

    @Test
    void rejectsOlderOpenApiContracts() throws Exception {
        Path contract = project.resolve("legacy.yaml");
        Files.writeString(contract, "openapi: 3.0.3\npaths: {}\n");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SpringOpenApiGenerator()
                                .generate(
                                        ProjectConfiguration.defaults(
                                                "legacy", Architecture.LAYERED),
                                        project,
                                        contract,
                                        "shared"));
    }
}

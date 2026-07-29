package io.backsmith.adapter.spring;

import java.util.Locale;
import java.util.Set;

record DatabaseProfile(
        String id,
        boolean relational,
        String jdbcUrl,
        String username,
        String password,
        String uuidType,
        String timestampType,
        String jsonType,
        String textType,
        String binaryType,
        String booleanType,
        String trueLiteral,
        String testJdbcUrl,
        String testUsername,
        String testPassword) {

    static final Set<String> SUPPORTED =
            Set.of("postgresql", "mysql", "mariadb", "sqlserver", "oracle", "h2", "mongodb");

    static DatabaseProfile from(String value) {
        String id = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "postgresql" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:postgresql://localhost:5432/{{artifactId}}",
                            "postgres",
                            "postgres",
                            "UUID",
                            "TIMESTAMPTZ",
                            "JSONB",
                            "TEXT",
                            "BYTEA",
                            "BOOLEAN",
                            "TRUE",
                            "jdbc:tc:postgresql:17-alpine:///{{artifactId}}",
                            "test",
                            "test");
            case "mysql" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:mysql://localhost:3306/{{artifactId}}?createDatabaseIfNotExist=true",
                            "backsmith",
                            "backsmith",
                            "BINARY(16)",
                            "DATETIME(6)",
                            "JSON",
                            "LONGTEXT",
                            "LONGBLOB",
                            "BOOLEAN",
                            "TRUE",
                            "jdbc:tc:mysql:8.4.6:///{{artifactId}}",
                            "test",
                            "test");
            case "mariadb" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:mariadb://localhost:3306/{{artifactId}}",
                            "backsmith",
                            "backsmith",
                            "BINARY(16)",
                            "DATETIME(6)",
                            "JSON",
                            "LONGTEXT",
                            "LONGBLOB",
                            "BOOLEAN",
                            "TRUE",
                            "jdbc:tc:mariadb:11.8.3:///{{artifactId}}",
                            "test",
                            "test");
            case "sqlserver" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:sqlserver://localhost:1433;databaseName={{artifactId}};encrypt=true;trustServerCertificate=true",
                            "sa",
                            "Backsmith1!",
                            "UNIQUEIDENTIFIER",
                            "DATETIMEOFFSET(6)",
                            "NVARCHAR(MAX)",
                            "NVARCHAR(MAX)",
                            "VARBINARY(MAX)",
                            "BIT",
                            "1",
                            "jdbc:tc:sqlserver:2022-CU20-ubuntu-22.04:///{{artifactId}}",
                            "sa",
                            "A_Str0ng_Required_Password");
            case "oracle" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
                            "backsmith",
                            "backsmith",
                            "RAW(16)",
                            "TIMESTAMP(6) WITH TIME ZONE",
                            "CLOB",
                            "CLOB",
                            "BLOB",
                            "NUMBER(1)",
                            "1",
                            "jdbc:tc:oracle:23-slim-faststart:///{{artifactId}}",
                            "test",
                            "test");
            case "h2" ->
                    new DatabaseProfile(
                            id,
                            true,
                            "jdbc:h2:file:./data/{{artifactId}};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                            "sa",
                            "",
                            "UUID",
                            "TIMESTAMP WITH TIME ZONE",
                            "JSON",
                            "CLOB",
                            "BLOB",
                            "BOOLEAN",
                            "TRUE",
                            "jdbc:h2:mem:{{artifactId}};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                            "sa",
                            "");
            case "mongodb" ->
                    new DatabaseProfile(
                            id,
                            false,
                            "mongodb://localhost:27017/{{artifactId}}",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "");
            default ->
                    throw new IllegalArgumentException(
                            "database: supported values are "
                                    + String.join(", ", SUPPORTED.stream().sorted().toList()));
        };
    }

    boolean mongodb() {
        return id.equals("mongodb");
    }

    boolean h2() {
        return id.equals("h2");
    }

    String persistence() {
        return mongodb() ? "mongodb" : "jpa";
    }

    String migrations() {
        return mongodb() ? "none" : "flyway";
    }

    String deploymentUrl() {
        return jdbcUrl.replace("localhost", "database");
    }
}

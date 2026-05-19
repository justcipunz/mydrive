package mydrive.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

public record ClientConfig(
        String clientId,
        Path clientDir,
        String serverIp,
        int serverPort,
        int maxConnections,
        String transferMode,
        Path metricsCsvPath,
        Path clientIdFile
) {
    public static ClientConfig load() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ClientConfig.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (in == null) {
                throw new IllegalStateException("client.properties is missing");
            }
            properties.load(in);
        }

        Path clientDir = Paths.get(required(properties, "client.dir")).normalize();
        Path clientIdFile = Paths.get(optional(properties, "client.id.file", "./.mydrive-client-id")).normalize();
        String clientId = resolvePersistentClientId(properties, clientIdFile);

        String serverIp = required(properties, "server.ip");
        int serverPort = Integer.parseInt(required(properties, "server.port"));
        int maxConnections = Integer.parseInt(required(properties, "max.connections"));
        String transferMode = normalizeTransferMode(required(properties, "transfer.mode"));
        Path metricsCsvPath = Paths.get(optional(properties, "metrics.csv.path", "./metrics/transfer-metrics.csv")).normalize();

        if (maxConnections < 1 || maxConnections > 32) {
            throw new IllegalArgumentException("max.connections must be in range 1..32");
        }
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("server.port must be in range 1..65535");
        }

        return new ClientConfig(clientId, clientDir, serverIp, serverPort, maxConnections, transferMode, metricsCsvPath, clientIdFile);
    }

    private static String normalizeTransferMode(String transferModeRaw) {
        String mode = transferModeRaw.trim().toUpperCase(Locale.ROOT);
        if (!"DMA".equals(mode) && !"NON_DMA".equals(mode)) {
            throw new IllegalArgumentException("transfer.mode must be either DMA or NON_DMA, got: " + transferModeRaw);
        }
        return mode;
    }

    private static String resolvePersistentClientId(Properties properties, Path clientIdFile) throws IOException {
        String configured = optional(properties, "client.id", "");
        if (!configured.isEmpty()) {
            return configured;
        }

        if (Files.exists(clientIdFile)) {
            String existing = Files.readString(clientIdFile, StandardCharsets.UTF_8).trim();
            if (!existing.isEmpty()) {
                return existing;
            }
        }

        if (clientIdFile.getParent() != null) {
            Files.createDirectories(clientIdFile.getParent());
        }
        String generated = "client-" + UUID.randomUUID();
        Files.writeString(clientIdFile, generated + System.lineSeparator(), StandardCharsets.UTF_8);
        return generated;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Property is blank: " + key);
        }
        return trimmed;
    }

    private static String optional(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
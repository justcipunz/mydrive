package mydrive.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public record ServerConfig(String bindIp, int bindPort, Path storageRoot) {
    public static ServerConfig load() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ServerConfig.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in == null) {
                throw new IllegalStateException("server.properties is missing");
            }
            properties.load(in);
        }

        String bindIp = requiredAny(properties, "server.bind.ip", "server.ip");
        int bindPort = Integer.parseInt(requiredAny(properties, "server.bind.port", "server.port"));
        Path storageRoot = Paths.get(requiredAny(properties, "server.storage.root", "storage.root")).normalize();

        if (bindPort < 1 || bindPort > 65535) {
            throw new IllegalArgumentException("server.bind.port must be in range 1..65535");
        }

        return new ServerConfig(bindIp, bindPort, storageRoot);
    }

    private static String requiredAny(Properties properties, String primaryKey, String fallbackKey) {
        String value = properties.getProperty(primaryKey);
        if (value == null) {
            value = properties.getProperty(fallbackKey);
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + primaryKey);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Property is blank: " + primaryKey);
        }
        return trimmed;
    }
}
package mydrive.server.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mydrive.common.io.ChecksumService;
import mydrive.common.protocol.messages.FileMetadata;

public final class ServerStorageService {
    private final Path storageRoot;

    public ServerStorageService(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public List<FileMetadata> listClientFiles(String clientId) throws IOException {
        Path clientDir = storageRoot.resolve(clientId).normalize();
        if (!Files.exists(clientDir)) {
            Files.createDirectories(clientDir);
            return List.of();
        }

        List<FileMetadata> files = new ArrayList<>();
        try (var stream = Files.list(clientDir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> files.add(toMetadata(path)));
        }
        return files;
    }

    public Path clientDir(String clientId) {
        return storageRoot.resolve(clientId).normalize();
    }

    private FileMetadata toMetadata(Path path) {
        try {
            return new FileMetadata(path.getFileName().toString(), Files.size(path), ChecksumService.sha256Hex(path));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read storage file: " + path, e);
        }
    }
}
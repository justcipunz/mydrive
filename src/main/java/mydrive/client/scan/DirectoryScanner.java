package mydrive.client.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mydrive.common.io.ChecksumService;
import mydrive.common.protocol.messages.FileMetadata;

public final class DirectoryScanner {
    public List<FileMetadata> scanFlat(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        List<FileMetadata> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> files.add(toMetadata(path)));
        }
        return files;
    }

    private FileMetadata toMetadata(Path path) {
        try {
            return new FileMetadata(
                    path.getFileName().toString(),
                    Files.size(path),
                    ChecksumService.sha256Hex(path)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan file: " + path, e);
        }
    }
}
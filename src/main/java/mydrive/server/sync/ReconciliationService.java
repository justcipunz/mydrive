package mydrive.server.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReconciliationService {
    public int deleteExtraFiles(Path clientStorageDir, List<String> extraServerFiles) throws IOException {
        int deleted = 0;
        for (String name : extraServerFiles) {
            Path path = safeResolveFile(clientStorageDir, name);
            if (Files.deleteIfExists(path)) {
                deleted++;
            }
        }
        return deleted;
    }

    public static Path safeResolveFile(Path clientStorageDir, String fileName) {
        validateFileName(fileName);
        Path normalizedBase = clientStorageDir.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(fileName).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Resolved path escapes client storage directory: " + fileName);
        }
        return resolved;
    }

    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be empty");
        }
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("File name must not contain path separators: " + fileName);
        }
        if (fileName.contains("..")) {
            throw new IllegalArgumentException("File name must not contain '..': " + fileName);
        }
    }
}
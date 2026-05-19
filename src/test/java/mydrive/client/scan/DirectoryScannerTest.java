package mydrive.client.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mydrive.common.io.ChecksumService;
import mydrive.common.protocol.messages.FileMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansOnlyRegularFilesWithoutSubdirectories() throws Exception {
        Path fileA = tempDir.resolve("a.txt");
        Path fileB = tempDir.resolve("b.txt");
        Files.writeString(fileA, "hello", StandardCharsets.UTF_8);
        Files.writeString(fileB, "world", StandardCharsets.UTF_8);

        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "nested", StandardCharsets.UTF_8);

        DirectoryScanner scanner = new DirectoryScanner();
        List<FileMetadata> files = scanner.scanFlat(tempDir);

        assertEquals(2, files.size());
        assertEquals("a.txt", files.get(0).name());
        assertEquals(Files.size(fileA), files.get(0).sizeBytes());
        assertEquals(ChecksumService.sha256Hex(fileA), files.get(0).checksumHex());
        assertEquals("b.txt", files.get(1).name());
    }
}
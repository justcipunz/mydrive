package mydrive.server.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconciliationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void deletesExtraFiles() throws Exception {
        Path userDir = tempDir.resolve("user");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("x.txt"), "x");
        Files.writeString(userDir.resolve("y.txt"), "y");

        ReconciliationService service = new ReconciliationService();
        int deleted = service.deleteExtraFiles(userDir, List.of("x.txt", "y.txt"));

        assertEquals(2, deleted);
    }

    @Test
    void doesNotEscapeUserDirectory() throws Exception {
        Path userDir = tempDir.resolve("user");
        Files.createDirectories(userDir);

        ReconciliationService service = new ReconciliationService();
        assertThrows(IllegalArgumentException.class, () -> service.deleteExtraFiles(userDir, List.of("../outside.txt")));
    }

    @Test
    void handlesEmptyList() throws Exception {
        Path userDir = tempDir.resolve("user");
        Files.createDirectories(userDir);

        ReconciliationService service = new ReconciliationService();
        int deleted = service.deleteExtraFiles(userDir, List.of());

        assertEquals(0, deleted);
    }
}
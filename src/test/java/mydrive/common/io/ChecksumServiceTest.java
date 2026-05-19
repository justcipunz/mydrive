package mydrive.common.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void sha256KnownContent() throws Exception {
        Path file = tempDir.resolve("known.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);
        String checksum = ChecksumService.sha256Hex(file);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", checksum);
    }
}
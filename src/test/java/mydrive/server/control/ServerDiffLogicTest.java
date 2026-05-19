package mydrive.server.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import mydrive.common.protocol.messages.FileMetadata;
import org.junit.jupiter.api.Test;

class ServerDiffLogicTest {
    @Test
    void diffMissingChangedExtraFiles() {
        FileMetadata clientMissing = new FileMetadata("missing.txt", 10, "aaa");
        FileMetadata clientChanged = new FileMetadata("changed.txt", 20, "new-hash");
        FileMetadata clientSame = new FileMetadata("same.txt", 30, "same-hash");

        FileMetadata serverChanged = new FileMetadata("changed.txt", 20, "old-hash");
        FileMetadata serverSame = new FileMetadata("same.txt", 30, "same-hash");
        FileMetadata serverExtra = new FileMetadata("extra.txt", 1, "x");

        var result = ServerDiffUtil.diff(
                List.of(clientMissing, clientChanged, clientSame),
                List.of(serverChanged, serverSame, serverExtra)
        );

        assertEquals(List.of(clientMissing, clientChanged), result.requiredFiles());
        assertEquals(List.of("extra.txt"), result.extraServerFiles());
    }
}
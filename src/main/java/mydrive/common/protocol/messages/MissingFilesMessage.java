package mydrive.common.protocol.messages;

import java.util.List;

public record MissingFilesMessage(
        String syncId,
        List<FileMetadata> requiredFiles,
        List<String> extraServerFiles,
        long serverDecisionAtEpochMs
) {
}
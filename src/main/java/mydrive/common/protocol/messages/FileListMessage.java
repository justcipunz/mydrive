package mydrive.common.protocol.messages;

import java.util.List;

public record FileListMessage(String syncId, List<FileMetadata> files, long scanCompletedAtEpochMs) {
}
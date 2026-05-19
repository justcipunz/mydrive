package mydrive.common.protocol.messages;

public record SyncCompleteMessage(
        String syncId,
        boolean success,
        int filesReceived,
        long bytesReceived,
        int deletedExtraFiles,
        String serverStoragePath,
        long finishedAtEpochMs,
        String reason
) {
}
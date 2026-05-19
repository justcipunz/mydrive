package mydrive.common.protocol.messages;

public record FileTransferCompleteMessage(
        String syncId,
        String transferId,
        long bytesProcessed,
        long durationMs,
        Boolean stored,
        String checksumHex,
        String reason
) {
}
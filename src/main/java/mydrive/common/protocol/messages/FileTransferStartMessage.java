package mydrive.common.protocol.messages;

public record FileTransferStartMessage(
        String syncId,
        String transferId,
        String clientId,
        String fileName,
        long sizeBytes,
        String checksumHex,
        String transferMode,
        int chunkSizeBytes
) {
}
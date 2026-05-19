package mydrive.common.protocol.messages;

public record FileMetadata(String name, long sizeBytes, String checksumHex) {
}
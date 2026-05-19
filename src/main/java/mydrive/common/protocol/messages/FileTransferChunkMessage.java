package mydrive.common.protocol.messages;

public record FileTransferChunkMessage(String transferId, byte[] bytes) {
}
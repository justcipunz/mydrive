package mydrive.common.protocol.messages;

public record FileTransferAckMessage(String syncId, String transferId, boolean accepted, String reason) {
}
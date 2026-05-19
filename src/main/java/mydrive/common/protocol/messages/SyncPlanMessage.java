package mydrive.common.protocol.messages;

public record SyncPlanMessage(String syncId, int totalFilesToSend, long totalBytesToSend, int parallelConnections) {
}
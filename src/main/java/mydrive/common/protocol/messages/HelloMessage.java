package mydrive.common.protocol.messages;

public record HelloMessage(
        String clientId,
        String protocolVersion,
        int maxConnections,
        String transferMode,
        long clientTimeEpochMs
) {
}
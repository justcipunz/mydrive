package mydrive.common.protocol.messages;

public record ErrorMessage(
        String code,
        String message,
        String syncId,
        String transferId,
        boolean fatal
) {
}
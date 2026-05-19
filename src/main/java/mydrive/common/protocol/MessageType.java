package mydrive.common.protocol;

public enum MessageType {
    HELLO(1),
    FILE_LIST(2),
    MISSING_FILES(3),
    SYNC_PLAN(4),
    FILE_TRANSFER_START(5),
    FILE_TRANSFER_ACK(6),
    FILE_TRANSFER_COMPLETE(7),
    SYNC_COMPLETE(8),
    ERROR(9),
    PING(10),
    PONG(11),
    FILE_TRANSFER_CHUNK(12);

    private final byte code;

    MessageType(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
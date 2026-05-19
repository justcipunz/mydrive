package mydrive.common.protocol;

import java.util.Arrays;

public final class ProtocolFrame {
    private final MessageType type;
    private final long requestId;
    private final byte[] payload;

    public ProtocolFrame(MessageType type, long requestId, byte[] payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public MessageType type() {
        return type;
    }

    public long requestId() {
        return requestId;
    }

    public byte[] payload() {
        return payload;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProtocolFrame other)) {
            return false;
        }
        return requestId == other.requestId
                && type == other.type
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Long.hashCode(requestId);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
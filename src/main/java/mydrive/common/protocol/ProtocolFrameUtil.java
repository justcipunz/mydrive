package mydrive.common.protocol;

public final class ProtocolFrameUtil {
    private ProtocolFrameUtil() {
    }

    public static ProtocolFrame of(MessageType type, long requestId, Object payload) {
        return new ProtocolFrame(type, requestId, ProtocolJson.write(payload));
    }

    public static <T> T readPayload(ProtocolFrame frame, Class<T> type) {
        return ProtocolJson.read(frame.payload(), type);
    }
}
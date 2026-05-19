package mydrive.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public final class ProtocolJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProtocolJson() {
    }

    public static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize protocol payload", e);
        }
    }

    public static <T> T read(byte[] payload, Class<T> type) {
        try {
            return MAPPER.readValue(payload, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize protocol payload", e);
        }
    }
}
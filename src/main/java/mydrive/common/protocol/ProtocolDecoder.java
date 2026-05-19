package mydrive.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;

public final class ProtocolDecoder extends ByteToMessageDecoder {
    private static final int HEADER_SIZE = Integer.BYTES + 1 + Long.BYTES;
    private static final int MIN_FRAME_LENGTH = 1 + Long.BYTES;
    private final int maxFrameLength;

    public ProtocolDecoder(int maxFrameLength) {
        if (maxFrameLength < MIN_FRAME_LENGTH) {
            throw new IllegalArgumentException("maxFrameLength too small");
        }
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }

        in.markReaderIndex();
        int frameLength = in.readInt();

        if (frameLength < MIN_FRAME_LENGTH) {
            throw new CorruptedFrameException("Frame length is too small: " + frameLength);
        }
        if (frameLength > maxFrameLength) {
            throw new CorruptedFrameException("Frame length exceeds max: " + frameLength);
        }
        if (in.readableBytes() < frameLength) {
            in.resetReaderIndex();
            return;
        }

        byte typeCode = in.readByte();
        long requestId = in.readLong();
        int payloadLength = frameLength - 1 - Long.BYTES;

        byte[] payload = new byte[payloadLength];
        in.readBytes(payload);

        MessageType type = MessageType.fromCode(typeCode);
        out.add(new ProtocolFrame(type, requestId, payload));
    }

    public static int minFrameBytes() {
        return HEADER_SIZE;
    }
}
package mydrive.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class ProtocolEncoder extends MessageToByteEncoder<ProtocolFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolFrame frame, ByteBuf out) {
        int frameLength = 1 + Long.BYTES + frame.payload().length;
        out.writeInt(frameLength);
        out.writeByte(frame.type().code());
        out.writeLong(frame.requestId());
        out.writeBytes(frame.payload());
    }
}
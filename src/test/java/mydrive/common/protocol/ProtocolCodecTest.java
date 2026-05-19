package mydrive.common.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    @Test
    void roundTripFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtocolEncoder(), new ProtocolDecoder(1024 * 1024));
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        ProtocolFrame outFrame = new ProtocolFrame(MessageType.HELLO, 42L, payload);

        channel.writeOutbound(outFrame);
        ByteBuf encoded = channel.readOutbound();

        channel.writeInbound(encoded.retain());
        ProtocolFrame inFrame = channel.readInbound();

        assertEquals(MessageType.HELLO, inFrame.type());
        assertEquals(42L, inFrame.requestId());
        assertArrayEquals(payload, inFrame.payload());

        encoded.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void decodesWhenTcpSplitsFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtocolDecoder(1024 * 1024));
        ProtocolFrame frame = new ProtocolFrame(MessageType.PING, 100L, new byte[] {1, 2, 3, 4});

        EmbeddedChannel encoderChannel = new EmbeddedChannel(new ProtocolEncoder());
        encoderChannel.writeOutbound(frame);
        ByteBuf encoded = encoderChannel.readOutbound();

        ByteBuf part1 = encoded.readRetainedSlice(3);
        ByteBuf part2 = encoded.readRetainedSlice(encoded.readableBytes());

        channel.writeInbound(part1);
        assertNull(channel.readInbound());

        channel.writeInbound(part2);
        ProtocolFrame decoded = channel.readInbound();

        assertEquals(frame.type(), decoded.type());
        assertEquals(frame.requestId(), decoded.requestId());
        assertArrayEquals(frame.payload(), decoded.payload());

        encoded.release();
        encoderChannel.finishAndReleaseAll();
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectsTooLargeFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtocolDecoder(16));
        ByteBuf bad = Unpooled.buffer();
        bad.writeInt(17);

        assertThrows(CorruptedFrameException.class, () -> channel.writeInbound(bad));
        channel.finishAndReleaseAll();
    }

    @Test
    void decodesTwoFramesFromSingleByteBuf() {
        EmbeddedChannel decoder = new EmbeddedChannel(new ProtocolDecoder(1024 * 1024));
        EmbeddedChannel encoder = new EmbeddedChannel(new ProtocolEncoder());

        ProtocolFrame one = new ProtocolFrame(MessageType.PING, 1L, new byte[] {1});
        ProtocolFrame two = new ProtocolFrame(MessageType.PONG, 2L, new byte[] {2, 3});

        encoder.writeOutbound(one);
        encoder.writeOutbound(two);
        ByteBuf b1 = encoder.readOutbound();
        ByteBuf b2 = encoder.readOutbound();
        ByteBuf merged = Unpooled.wrappedBuffer(b1.retain(), b2.retain());

        decoder.writeInbound(merged);
        ProtocolFrame r1 = decoder.readInbound();
        ProtocolFrame r2 = decoder.readInbound();

        assertEquals(MessageType.PING, r1.type());
        assertEquals(1L, r1.requestId());
        assertEquals(MessageType.PONG, r2.type());
        assertEquals(2L, r2.requestId());

        b1.release();
        b2.release();
        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
    }

    @Test
    void keepsSecondFrameWhenOnlyItsPrefixArrived() {
        EmbeddedChannel decoder = new EmbeddedChannel(new ProtocolDecoder(1024 * 1024));
        EmbeddedChannel encoder = new EmbeddedChannel(new ProtocolEncoder());

        ProtocolFrame one = new ProtocolFrame(MessageType.PING, 11L, new byte[] {9});
        ProtocolFrame two = new ProtocolFrame(MessageType.PONG, 12L, new byte[] {7, 8, 9, 10});

        encoder.writeOutbound(one);
        encoder.writeOutbound(two);
        ByteBuf b1 = encoder.readOutbound();
        ByteBuf b2 = encoder.readOutbound();

        ByteBuf firstPlusSecondPrefix = Unpooled.buffer();
        firstPlusSecondPrefix.writeBytes(b1, b1.readableBytes());
        firstPlusSecondPrefix.writeBytes(b2, 0, 5);

        decoder.writeInbound(firstPlusSecondPrefix);
        ProtocolFrame r1 = decoder.readInbound();
        assertEquals(11L, r1.requestId());
        assertNull(decoder.readInbound());

        ByteBuf secondRest = b2.retainedSlice(5, b2.readableBytes() - 5);
        decoder.writeInbound(secondRest);
        ProtocolFrame r2 = decoder.readInbound();
        assertEquals(12L, r2.requestId());

        b1.release();
        b2.release();
        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
    }
}

package mydrive.client.transfer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolFrame;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.FileTransferAckMessage;
import mydrive.common.protocol.messages.FileTransferCompleteMessage;

public final class TransferClientHandler extends SimpleChannelInboundHandler<ProtocolFrame> {
    private final CompletableFuture<FileTransferAckMessage> ackFuture;
    private final CompletableFuture<FileTransferCompleteMessage> completeFuture;

    public TransferClientHandler(
            CompletableFuture<FileTransferAckMessage> ackFuture,
            CompletableFuture<FileTransferCompleteMessage> completeFuture
    ) {
        this.ackFuture = ackFuture;
        this.completeFuture = completeFuture;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolFrame frame) {
        if (frame.type() == MessageType.FILE_TRANSFER_ACK) {
            ackFuture.complete(ProtocolFrameUtil.readPayload(frame, FileTransferAckMessage.class));
            return;
        }
        if (frame.type() == MessageType.FILE_TRANSFER_COMPLETE) {
            completeFuture.complete(ProtocolFrameUtil.readPayload(frame, FileTransferCompleteMessage.class));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ackFuture.completeExceptionally(cause);
        completeFuture.completeExceptionally(cause);
        ctx.close();
    }
}
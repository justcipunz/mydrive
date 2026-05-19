package mydrive.client.control;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolFrame;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.FileTransferAckMessage;
import mydrive.common.protocol.messages.FileTransferCompleteMessage;
import mydrive.common.protocol.messages.MissingFilesMessage;
import mydrive.common.protocol.messages.SyncCompleteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ControlClientHandler extends SimpleChannelInboundHandler<ProtocolFrame> {
    private static final Logger log = LoggerFactory.getLogger(ControlClientHandler.class);
    private final CompletableFuture<MissingFilesMessage> missingFilesFuture;
    private final CompletableFuture<SyncCompleteMessage> syncCompleteFuture;
    private final Map<String, CompletableFuture<FileTransferAckMessage>> ackFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<FileTransferCompleteMessage>> completeFutures = new ConcurrentHashMap<>();

    public ControlClientHandler(
            CompletableFuture<MissingFilesMessage> missingFilesFuture,
            CompletableFuture<SyncCompleteMessage> syncCompleteFuture
    ) {
        this.missingFilesFuture = missingFilesFuture;
        this.syncCompleteFuture = syncCompleteFuture;
    }

    public CompletableFuture<FileTransferAckMessage> registerAckFuture(String transferId) {
        CompletableFuture<FileTransferAckMessage> future = new CompletableFuture<>();
        ackFutures.put(transferId, future);
        return future;
    }

    public CompletableFuture<FileTransferCompleteMessage> registerCompleteFuture(String transferId) {
        CompletableFuture<FileTransferCompleteMessage> future = new CompletableFuture<>();
        completeFutures.put(transferId, future);
        return future;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolFrame frame) {
        if (frame.type() == MessageType.MISSING_FILES) {
            missingFilesFuture.complete(ProtocolFrameUtil.readPayload(frame, MissingFilesMessage.class));
            return;
        }
        if (frame.type() == MessageType.FILE_TRANSFER_ACK) {
            FileTransferAckMessage msg = ProtocolFrameUtil.readPayload(frame, FileTransferAckMessage.class);
            CompletableFuture<FileTransferAckMessage> future = ackFutures.remove(msg.transferId());
            if (future != null) {
                future.complete(msg);
            }
            return;
        }
        if (frame.type() == MessageType.FILE_TRANSFER_COMPLETE) {
            FileTransferCompleteMessage msg = ProtocolFrameUtil.readPayload(frame, FileTransferCompleteMessage.class);
            CompletableFuture<FileTransferCompleteMessage> future = completeFutures.remove(msg.transferId());
            if (future != null) {
                future.complete(msg);
            }
            return;
        }
        if (frame.type() == MessageType.SYNC_COMPLETE) {
            syncCompleteFuture.complete(ProtocolFrameUtil.readPayload(frame, SyncCompleteMessage.class));
            return;
        }

        log.warn("Unexpected control message from server: {}", frame.type());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        missingFilesFuture.completeExceptionally(cause);
        syncCompleteFuture.completeExceptionally(cause);
        ackFutures.values().forEach(f -> f.completeExceptionally(cause));
        completeFutures.values().forEach(f -> f.completeExceptionally(cause));
        ctx.close();
    }
}
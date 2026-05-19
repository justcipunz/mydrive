package mydrive.server.control;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolDecoder;
import mydrive.common.protocol.ProtocolFrame;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.FileListMessage;
import mydrive.common.protocol.messages.FileTransferAckMessage;
import mydrive.common.protocol.messages.FileTransferChunkMessage;
import mydrive.common.protocol.messages.FileTransferCompleteMessage;
import mydrive.common.protocol.messages.FileTransferStartMessage;
import mydrive.common.protocol.messages.HelloMessage;
import mydrive.common.protocol.messages.MissingFilesMessage;
import mydrive.common.protocol.messages.SyncPlanMessage;
import mydrive.server.storage.ServerStorageService;
import mydrive.server.sync.ReconciliationService;
import mydrive.server.sync.SyncSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientSessionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ClientSessionHandler.class);
    private final ServerStorageService storageService;
    private final SyncSessionRegistry syncSessionRegistry;
    private final Map<String, TransferState> transfers = new HashMap<>();

    private String clientId;
    private String syncId;

    public ClientSessionHandler(ServerStorageService storageService, SyncSessionRegistry syncSessionRegistry) {
        this.storageService = storageService;
        this.syncSessionRegistry = syncSessionRegistry;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ProtocolFrame frame) {
            handleFrame(ctx, frame);
            return;
        }
        if (msg instanceof ByteBuf buf) {
            try {
                handleRawBytes(ctx, buf);
            } finally {
                buf.release();
            }
            return;
        }
        super.channelRead(ctx, msg);
    }

    private void handleFrame(ChannelHandlerContext ctx, ProtocolFrame frame) throws Exception {
        switch (frame.type()) {
            case HELLO -> handleHello(ctx, frame);
            case FILE_LIST -> handleFileList(ctx, frame);
            case SYNC_PLAN -> handleSyncPlan(ctx, frame);
            case FILE_TRANSFER_START -> handleTransferStart(ctx, frame);
            case FILE_TRANSFER_CHUNK -> handleTransferChunk(ctx, frame);
            case FILE_TRANSFER_COMPLETE -> handleTransferComplete(ctx, frame);
            default -> log.warn("conn={} remote={} unsupported message type={}", connectionId(ctx), remote(ctx), frame.type());
        }
    }

    private void handleHello(ChannelHandlerContext ctx, ProtocolFrame frame) {
        HelloMessage hello = ProtocolFrameUtil.readPayload(frame, HelloMessage.class);
        this.clientId = hello.clientId();
        log.info("conn={} remote={} HELLO clientId={} maxConnections={} transferMode={}",
                connectionId(ctx), remote(ctx), hello.clientId(), hello.maxConnections(), hello.transferMode());
    }

    private void handleFileList(ChannelHandlerContext ctx, ProtocolFrame frame) throws Exception {
        if (clientId == null) {
            throw new IllegalStateException("HELLO must be received before FILE_LIST");
        }
        FileListMessage listMessage = ProtocolFrameUtil.readPayload(frame, FileListMessage.class);
        var serverFiles = storageService.listClientFiles(clientId);
        var diff = ServerDiffUtil.diff(listMessage.files(), serverFiles);

        syncId = listMessage.syncId();
        syncSessionRegistry.initSession(syncId, clientId, diff.extraServerFiles(), storageService.clientDir(clientId));

        MissingFilesMessage response = new MissingFilesMessage(
                listMessage.syncId(), diff.requiredFiles(), diff.extraServerFiles(), Instant.now().toEpochMilli());

        log.info("conn={} remote={} syncId={} FILE_LIST required={} extra={}",
                connectionId(ctx), remote(ctx), syncId, diff.requiredFiles().size(), diff.extraServerFiles().size());
        ctx.writeAndFlush(ProtocolFrameUtil.of(MessageType.MISSING_FILES, frame.requestId(), response));
    }

    private void handleSyncPlan(ChannelHandlerContext ctx, ProtocolFrame frame) throws Exception {
        SyncPlanMessage plan = ProtocolFrameUtil.readPayload(frame, SyncPlanMessage.class);
        syncId = plan.syncId();
        syncSessionRegistry.setExpectedFiles(syncId, plan.totalFilesToSend());
        syncSessionRegistry.attachControl(syncId, ctx, frame.requestId());
        log.info("conn={} remote={} syncId={} SYNC_PLAN expectedFiles={} bytes={} parallel={}",
                connectionId(ctx), remote(ctx), syncId, plan.totalFilesToSend(), plan.totalBytesToSend(), plan.parallelConnections());
    }

    private void handleTransferStart(ChannelHandlerContext ctx, ProtocolFrame frame) throws Exception {
        FileTransferStartMessage msg = ProtocolFrameUtil.readPayload(frame, FileTransferStartMessage.class);
        if (this.clientId == null) {
            this.clientId = msg.clientId();
        }
        this.syncId = msg.syncId();

        Path clientDir = storageService.clientDir(clientId);
        Files.createDirectories(clientDir);

        ReconciliationService.validateFileName(msg.fileName());
        Path finalPath = ReconciliationService.safeResolveFile(clientDir, msg.fileName());
        Path tempPath = finalPath.getParent().resolve(msg.fileName() + ".part." + msg.transferId()).normalize();
        if (!tempPath.startsWith(clientDir.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Temp path escapes client storage directory");
        }

        OutputStream out = Files.newOutputStream(tempPath);
        boolean dmaMode = "DMA".equalsIgnoreCase(msg.transferMode());
        TransferState state = new TransferState(msg, tempPath, finalPath, out, MessageDigest.getInstance("SHA-256"), dmaMode);
        transfers.put(msg.transferId(), state);

        log.info("conn={} remote={} syncId={} transferId={} transfer-start file={} size={} mode={}",
                connectionId(ctx), remote(ctx), msg.syncId(), msg.transferId(), msg.fileName(), msg.sizeBytes(), msg.transferMode());

        FileTransferAckMessage ack = new FileTransferAckMessage(msg.syncId(), msg.transferId(), true, "");
        ctx.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_TRANSFER_ACK, frame.requestId(), ack));

        if (dmaMode && msg.sizeBytes() == 0L) {
            completeTransferAndRespond(ctx, msg.transferId(), msg.syncId(), frame.requestId(), 1L);
            return;
        }

        if (dmaMode && ctx.pipeline().get(ProtocolDecoder.class) != null) {
            ctx.pipeline().remove(ProtocolDecoder.class);
        }
    }

    private void handleTransferChunk(ChannelHandlerContext ctx, ProtocolFrame frame) throws IOException {
        FileTransferChunkMessage chunk = ProtocolFrameUtil.readPayload(frame, FileTransferChunkMessage.class);
        TransferState state = transfers.get(chunk.transferId());
        if (state == null) {
            throw new IllegalStateException("Unknown transferId for chunk: " + chunk.transferId());
        }
        state.out.write(chunk.bytes());
        state.digest.update(chunk.bytes());
        state.bytes += chunk.bytes().length;
        log.debug("conn={} remote={} transferId={} chunk-bytes={} total={}",
                connectionId(ctx), remote(ctx), chunk.transferId(), chunk.bytes().length, state.bytes);
    }

    private void handleRawBytes(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        for (TransferState state : transfers.values()) {
            if (!state.dmaMode) {
                continue;
            }
            while (buf.isReadable() && state.bytes < state.start.sizeBytes()) {
                int need = (int) Math.min(buf.readableBytes(), state.start.sizeBytes() - state.bytes);
                byte[] chunk = new byte[need];
                buf.readBytes(chunk);
                state.out.write(chunk);
                state.digest.update(chunk);
                state.bytes += need;
            }
            if (state.bytes == state.start.sizeBytes()) {
                completeTransferAndRespond(ctx, state.start.transferId(), state.start.syncId(), 0L, 1L);
            }
            break;
        }
    }

    private void handleTransferComplete(ChannelHandlerContext ctx, ProtocolFrame frame) throws Exception {
        FileTransferCompleteMessage msg = ProtocolFrameUtil.readPayload(frame, FileTransferCompleteMessage.class);
        completeTransferAndRespond(ctx, msg.transferId(), msg.syncId(), frame.requestId(), msg.durationMs());
    }

    private void completeTransferAndRespond(ChannelHandlerContext ctx, String transferId, String syncId, long requestId, long durationMs)
            throws Exception {
        TransferState state = transfers.remove(transferId);
        if (state == null) {
            return;
        }

        state.out.close();
        String actualChecksum = HexFormat.of().formatHex(state.digest.digest());
        boolean ok = state.bytes == state.start.sizeBytes() && actualChecksum.equals(state.start.checksumHex());
        String reason = ok ? "" : "size/checksum mismatch";

        if (ok) {
            Files.move(state.tempPath, state.finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            syncSessionRegistry.markTransferDone(syncId, state.bytes);
            log.info("conn={} remote={} syncId={} transferId={} transfer-complete file={} bytes={} ok=true",
                    connectionId(ctx), remote(ctx), syncId, transferId, state.start.fileName(), state.bytes);
        } else {
            Files.deleteIfExists(state.tempPath);
            log.warn("conn={} remote={} syncId={} transferId={} transfer-complete file={} bytes={} ok=false reason={}",
                    connectionId(ctx), remote(ctx), syncId, transferId, state.start.fileName(), state.bytes, reason);
        }

        FileTransferCompleteMessage response = new FileTransferCompleteMessage(syncId, transferId, state.bytes, durationMs, ok, actualChecksum, reason);
        ctx.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_TRANSFER_COMPLETE, requestId, response));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        for (TransferState state : transfers.values()) {
            try {
                state.out.close();
            } catch (IOException ignored) {
            }
            Files.deleteIfExists(state.tempPath);
        }
        transfers.clear();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("conn={} remote={} session handler failed", connectionId(ctx), remote(ctx), cause);
        ctx.close();
    }

    private static String connectionId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }

    private static String remote(ChannelHandlerContext ctx) {
        SocketAddress address = ctx.channel().remoteAddress();
        return address == null ? "n/a" : address.toString();
    }

    private static final class TransferState {
        private final FileTransferStartMessage start;
        private final Path tempPath;
        private final Path finalPath;
        private final OutputStream out;
        private final MessageDigest digest;
        private final boolean dmaMode;
        private long bytes;

        private TransferState(FileTransferStartMessage start, Path tempPath, Path finalPath, OutputStream out, MessageDigest digest, boolean dmaMode) {
            this.start = start;
            this.tempPath = tempPath;
            this.finalPath = finalPath;
            this.out = out;
            this.digest = digest;
            this.dmaMode = dmaMode;
        }
    }
}
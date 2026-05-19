package mydrive.server.sync;

import io.netty.channel.ChannelHandlerContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.SyncCompleteMessage;

public final class SyncSessionRegistry {
    private final Map<String, SyncSession> sessions = new ConcurrentHashMap<>();
    private final ReconciliationService reconciliationService = new ReconciliationService();

    public void initSession(String syncId, String clientId, List<String> extraServerFiles, Path clientStorageDir) {
        sessions.computeIfAbsent(syncId, k -> new SyncSession(syncId, clientId, extraServerFiles, clientStorageDir));
    }

    public void attachControl(String syncId, ChannelHandlerContext ctx, long requestId) throws Exception {
        SyncSession session = sessions.get(syncId);
        if (session == null) {
            return;
        }
        session.controlCtx = ctx;
        session.requestId = requestId;
        tryFinalize(session);
    }

    public void setExpectedFiles(String syncId, int expectedFiles) throws Exception {
        SyncSession session = sessions.get(syncId);
        if (session == null) {
            return;
        }
        session.expectedFiles.set(expectedFiles);
        tryFinalize(session);
    }

    public void markTransferDone(String syncId, long bytes) throws Exception {
        SyncSession session = sessions.get(syncId);
        if (session == null) {
            return;
        }
        session.completedFiles.incrementAndGet();
        session.receivedBytes.addAndGet(bytes);
        tryFinalize(session);
    }

    private void tryFinalize(SyncSession session) throws Exception {
        ChannelHandlerContext ctx = session.controlCtx;
        if (ctx == null) {
            return;
        }
        if (session.completedFiles.get() != session.expectedFiles.get()) {
            return;
        }
        if (!session.finalized.compareAndSet(false, true)) {
            return;
        }

        int deleted = reconciliationService.deleteExtraFiles(session.clientStorageDir, session.extraServerFiles);
        SyncCompleteMessage complete = new SyncCompleteMessage(
                session.syncId,
                true,
                session.completedFiles.get(),
                session.receivedBytes.get(),
                deleted,
                session.clientStorageDir.toString(),
                Instant.now().toEpochMilli(),
                ""
        );
        ctx.writeAndFlush(ProtocolFrameUtil.of(MessageType.SYNC_COMPLETE, session.requestId, complete));
        sessions.remove(session.syncId);
    }

    private static final class SyncSession {
        private final String syncId;
        private final String clientId;
        private final List<String> extraServerFiles;
        private final Path clientStorageDir;
        private final AtomicInteger expectedFiles = new AtomicInteger(0);
        private final AtomicInteger completedFiles = new AtomicInteger(0);
        private final AtomicLong receivedBytes = new AtomicLong(0);
        private final java.util.concurrent.atomic.AtomicBoolean finalized = new java.util.concurrent.atomic.AtomicBoolean(false);
        private volatile ChannelHandlerContext controlCtx;
        private volatile long requestId;

        private SyncSession(String syncId, String clientId, List<String> extraServerFiles, Path clientStorageDir) {
            this.syncId = syncId;
            this.clientId = clientId;
            this.extraServerFiles = List.copyOf(extraServerFiles);
            this.clientStorageDir = clientStorageDir;
        }
    }
}
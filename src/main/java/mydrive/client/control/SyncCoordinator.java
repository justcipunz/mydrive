package mydrive.client.control;

import io.netty.channel.Channel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.FileListMessage;
import mydrive.common.protocol.messages.FileMetadata;
import mydrive.common.protocol.messages.HelloMessage;
import mydrive.common.protocol.messages.MissingFilesMessage;
import mydrive.common.protocol.messages.SyncPlanMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SyncCoordinator {
    private static final Logger log = LoggerFactory.getLogger(SyncCoordinator.class);

    public SyncContext runControlHandshake(
            Channel channel,
            String clientId,
            int maxConnections,
            String transferMode,
            List<FileMetadata> clientFiles,
            CompletableFuture<MissingFilesMessage> missingFilesFuture
    ) throws Exception {
        long requestId = System.nanoTime();
        String syncId = UUID.randomUUID().toString();

        HelloMessage helloMessage = new HelloMessage(clientId, "1.0", maxConnections, transferMode,
                Instant.now().toEpochMilli());
        channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.HELLO, requestId, helloMessage)).sync();

        FileListMessage fileListMessage = new FileListMessage(syncId, clientFiles, Instant.now().toEpochMilli());
        channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_LIST, requestId, fileListMessage)).sync();

        MissingFilesMessage missingFilesMessage = missingFilesFuture.get(20, TimeUnit.SECONDS);

        int totalFiles = missingFilesMessage.requiredFiles().size();
        long totalBytes = missingFilesMessage.requiredFiles().stream().mapToLong(FileMetadata::sizeBytes).sum();
        int parallelConnections = Math.max(1, Math.min(maxConnections, Math.max(totalFiles, 1)));

        SyncPlanMessage plan = new SyncPlanMessage(syncId, totalFiles, totalBytes, parallelConnections);
        channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.SYNC_PLAN, requestId, plan)).sync();

        log.info("Control handshake done: syncId={}, requiredFiles={}", syncId, totalFiles);
        return new SyncContext(syncId, requestId, missingFilesMessage);
    }

    public record SyncContext(String syncId, long requestId, MissingFilesMessage missingFilesMessage) {
    }
}
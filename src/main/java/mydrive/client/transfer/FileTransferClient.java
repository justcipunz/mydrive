package mydrive.client.transfer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import mydrive.common.metrics.TransferMetricRecord;
import mydrive.common.protocol.MessageType;
import mydrive.common.protocol.ProtocolDecoder;
import mydrive.common.protocol.ProtocolEncoder;
import mydrive.common.protocol.ProtocolFrameUtil;
import mydrive.common.protocol.messages.FileMetadata;
import mydrive.common.protocol.messages.FileTransferAckMessage;
import mydrive.common.protocol.messages.FileTransferChunkMessage;
import mydrive.common.protocol.messages.FileTransferCompleteMessage;
import mydrive.common.protocol.messages.FileTransferStartMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileTransferClient {
    private static final Logger log = LoggerFactory.getLogger(FileTransferClient.class);
    private static final int CHUNK_SIZE = 64 * 1024;

    public TransferMetricRecord sendOneFile(
            EventLoopGroup group,
            String host,
            int port,
            String syncId,
            String clientId,
            Path clientDir,
            FileMetadata metadata,
            String transferMode,
            int maxConnections
    ) throws Exception {
        long requestId = System.nanoTime();
        String transferId = UUID.randomUUID().toString();
        Path file = clientDir.resolve(metadata.name());
        long startedAt = Instant.now().toEpochMilli();
        boolean dmaMode = "DMA".equalsIgnoreCase(transferMode);

        CompletableFuture<FileTransferAckMessage> ackFuture = new CompletableFuture<>();
        CompletableFuture<FileTransferCompleteMessage> completeFuture = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ProtocolDecoder(16 * 1024 * 1024));
                        ch.pipeline().addLast(new ProtocolEncoder());
                        ch.pipeline().addLast(new TransferClientHandler(ackFuture, completeFuture));
                    }
                });

        Channel channel = bootstrap.connect(host, port).sync().channel();
        String connId = channel.id().asShortText();
        String remote = String.valueOf(channel.remoteAddress());
        try {
            log.info("conn={} remote={} syncId={} transferId={} transfer-start file={} size={} mode={}",
                    connId, remote, syncId, transferId, metadata.name(), metadata.sizeBytes(), transferMode);

            FileTransferStartMessage start = new FileTransferStartMessage(
                    syncId, transferId, clientId, metadata.name(), metadata.sizeBytes(),
                    metadata.checksumHex(), transferMode, dmaMode ? 0 : CHUNK_SIZE
            );
            channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_TRANSFER_START, requestId, start)).sync();

            FileTransferAckMessage ack = ackFuture.get(20, TimeUnit.SECONDS);
            if (!ack.accepted()) {
                throw new IllegalStateException("Server rejected transfer: " + ack.reason());
            }

            if (dmaMode) {
                sendByDma(channel, file);
            } else {
                sendByChunks(channel, requestId, transferId, file);
                long durationMs = Math.max(1, Instant.now().toEpochMilli() - startedAt);
                FileTransferCompleteMessage complete = new FileTransferCompleteMessage(
                        syncId, transferId, metadata.sizeBytes(), durationMs, null, metadata.checksumHex(), null
                );
                channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_TRANSFER_COMPLETE, requestId, complete)).sync();
            }

            FileTransferCompleteMessage serverComplete = completeFuture.get(60, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(serverComplete.stored())) {
                throw new IllegalStateException("Server failed storing file: " + serverComplete.reason());
            }

            log.info("conn={} remote={} syncId={} transferId={} transfer-end file={} bytes={} durationMs={}",
                    connId, remote, syncId, transferId, metadata.name(), metadata.sizeBytes(),
                    Math.max(1, Instant.now().toEpochMilli() - startedAt));
        } finally {
            channel.close().sync();
        }

        long finishedAt = Instant.now().toEpochMilli();
        return new TransferMetricRecord(
                syncId,
                metadata.name(),
                metadata.sizeBytes(),
                Math.max(1, finishedAt - startedAt),
                transferMode,
                maxConnections,
                startedAt,
                finishedAt
        );
    }

    private static void sendByChunks(Channel channel, long requestId, String transferId, Path file) throws Exception {
        byte[] buffer = new byte[CHUNK_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                FileTransferChunkMessage chunkMessage = new FileTransferChunkMessage(transferId, chunk);
                channel.writeAndFlush(ProtocolFrameUtil.of(MessageType.FILE_TRANSFER_CHUNK, requestId, chunkMessage)).sync();
            }
        }
    }

    private static void sendByDma(Channel channel, Path file) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             FileChannel fc = raf.getChannel()) {
            channel.writeAndFlush(new DefaultFileRegion(fc, 0, fc.size())).sync();
        }
    }
}
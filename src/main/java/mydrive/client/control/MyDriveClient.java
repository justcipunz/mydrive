package mydrive.client.control;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import mydrive.client.config.ClientConfig;
import mydrive.client.scan.DirectoryScanner;
import mydrive.client.transfer.FileTransferClient;
import mydrive.common.metrics.MetricsCsvWriter;
import mydrive.common.metrics.TransferMetricRecord;
import mydrive.common.protocol.ProtocolDecoder;
import mydrive.common.protocol.ProtocolEncoder;
import mydrive.common.protocol.messages.MissingFilesMessage;
import mydrive.common.protocol.messages.SyncCompleteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MyDriveClient {
    private static final Logger log = LoggerFactory.getLogger(MyDriveClient.class);
    private final ClientConfig config;

    public MyDriveClient(ClientConfig config) {
        this.config = config;
    }

    public void runSingleSync() throws Exception {
        DirectoryScanner scanner = new DirectoryScanner();
        var files = scanner.scanFlat(config.clientDir());

        CompletableFuture<MissingFilesMessage> missingFilesFuture = new CompletableFuture<>();
        CompletableFuture<SyncCompleteMessage> syncCompleteFuture = new CompletableFuture<>();

        EventLoopGroup controlGroup = new NioEventLoopGroup(1);
        EventLoopGroup transferGroup = new NioEventLoopGroup(Math.max(1, config.maxConnections()));
        long syncStarted = System.currentTimeMillis();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(controlGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ControlClientHandler handler = new ControlClientHandler(missingFilesFuture, syncCompleteFuture);
                            ch.pipeline().addLast(new ProtocolDecoder(16 * 1024 * 1024));
                            ch.pipeline().addLast(new ProtocolEncoder());
                            ch.pipeline().addLast(handler);
                        }
                    });

            Channel controlChannel = bootstrap.connect(config.serverIp(), config.serverPort()).sync().channel();
            SyncCoordinator coordinator = new SyncCoordinator();
            SyncCoordinator.SyncContext context = coordinator.runControlHandshake(
                    controlChannel,
                    config.clientId(),
                    config.maxConnections(),
                    config.transferMode(),
                    files,
                    missingFilesFuture
            );
            log.info("sync-start syncId={} mode={} maxConnections={} scanFiles={} requiredFiles={}",
                    context.syncId(), config.transferMode(), config.maxConnections(), files.size(),
                    context.missingFilesMessage().requiredFiles().size());

            int parallel = Math.max(1, Math.min(config.maxConnections(), Math.max(1, context.missingFilesMessage().requiredFiles().size())));
            var pool = Executors.newFixedThreadPool(parallel);
            List<CompletableFuture<TransferMetricRecord>> transferTasks = new ArrayList<>();
            FileTransferClient transferClient = new FileTransferClient();

            for (var file : context.missingFilesMessage().requiredFiles()) {
                CompletableFuture<TransferMetricRecord> task = CompletableFuture.supplyAsync(() -> {
                    try {
                        return transferClient.sendOneFile(
                                transferGroup,
                                config.serverIp(),
                                config.serverPort(),
                                context.syncId(),
                                config.clientId(),
                                config.clientDir(),
                                file,
                                config.transferMode(),
                                config.maxConnections()
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, pool);
                transferTasks.add(task);
            }

            CompletableFuture.allOf(transferTasks.toArray(new CompletableFuture[0])).join();
            MetricsCsvWriter writer = new MetricsCsvWriter(config.metricsCsvPath());
            for (CompletableFuture<TransferMetricRecord> task : transferTasks) {
                writer.append(task.join());
            }
            pool.shutdown();

            SyncCompleteMessage complete = syncCompleteFuture.get(60, TimeUnit.SECONDS);
            log.info("sync-end syncId={} success={} filesReceived={} deletedExtraFiles={} durationMs={}",
                    complete.syncId(), complete.success(), complete.filesReceived(), complete.deletedExtraFiles(),
                    Math.max(1, System.currentTimeMillis() - syncStarted));

            controlChannel.close().sync();
        } finally {
            transferGroup.shutdownGracefully().sync();
            controlGroup.shutdownGracefully().sync();
        }
    }
}
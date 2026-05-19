package mydrive.server.control;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import mydrive.common.protocol.ProtocolDecoder;
import mydrive.common.protocol.ProtocolEncoder;
import mydrive.server.config.ServerConfig;
import mydrive.server.storage.ServerStorageService;
import mydrive.server.sync.SyncSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MyDriveServer {
    private static final Logger log = LoggerFactory.getLogger(MyDriveServer.class);
    private final ServerConfig config;
    private final ServerStorageService storageService;
    private final SyncSessionRegistry syncSessionRegistry;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    public MyDriveServer(ServerConfig config) {
        this.config = config;
        this.storageService = new ServerStorageService(config.storageRoot());
        this.syncSessionRegistry = new SyncSessionRegistry();
    }

    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ProtocolDecoder(16 * 1024 * 1024));
                        ch.pipeline().addLast(new ProtocolEncoder());
                        ch.pipeline().addLast(new ClientSessionHandler(storageService, syncSessionRegistry));
                    }
                });

        serverChannel = bootstrap.bind(config.bindIp(), config.bindPort()).sync().channel();
        log.info("Server started at {}:{} storageRoot={}", config.bindIp(), config.bindPort(), config.storageRoot());
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
        if (boss != null) {
            boss.shutdownGracefully();
        }
    }
}
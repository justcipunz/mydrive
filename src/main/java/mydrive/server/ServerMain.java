package mydrive.server;

import mydrive.server.config.ServerConfig;
import mydrive.server.control.MyDriveServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerMain {
    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) {
        MyDriveServer server = null;
        try {
            ServerConfig config = ServerConfig.load();
            server = new MyDriveServer(config);
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            log.error("Server failed", e);
            System.exit(1);
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
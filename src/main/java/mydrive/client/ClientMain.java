package mydrive.client;

import mydrive.client.cli.ConsoleCommandLoop;
import mydrive.client.config.ClientConfig;
import mydrive.client.control.MyDriveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientMain {
    private static final Logger log = LoggerFactory.getLogger(ClientMain.class);

    private ClientMain() {
    }

    public static void main(String[] args) {
        try {
            ClientConfig config = ClientConfig.load();
            log.info("Client config: client.id={} client.dir={} server={}:{} max.connections={} transfer.mode={} metrics.csv.path={}",
                    config.clientId(), config.clientDir(), config.serverIp(), config.serverPort(),
                    config.maxConnections(), config.transferMode(), config.metricsCsvPath());
            MyDriveClient client = new MyDriveClient(config);
            ConsoleCommandLoop loop = new ConsoleCommandLoop();
            loop.run(client, config);
        } catch (Exception e) {
            log.error("Client failed", e);
            System.exit(1);
        }
    }
}
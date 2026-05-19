package mydrive.client.cli;

import java.util.Scanner;
import mydrive.client.config.ClientConfig;
import mydrive.client.control.MyDriveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleCommandLoop {
    private static final Logger log = LoggerFactory.getLogger(ConsoleCommandLoop.class);

    public void run(MyDriveClient client, ClientConfig config) {
        try (Scanner scanner = new Scanner(System.in)) {
            log.info("Client CLI started. Commands: sync, show-config, show-metrics-path, exit");
            while (true) {
                System.out.print("mydrive> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String cmd = scanner.nextLine().trim();
                if (cmd.isEmpty()) {
                    continue;
                }
                if ("sync".equalsIgnoreCase(cmd)) {
                    runSync(client);
                    continue;
                }
                if ("show-config".equalsIgnoreCase(cmd)) {
                    showConfig(config);
                    continue;
                }
                if ("show-metrics-path".equalsIgnoreCase(cmd)) {
                    log.info("metrics.csv.path={}", config.metricsCsvPath());
                    continue;
                }
                if ("exit".equalsIgnoreCase(cmd)) {
                    log.info("Exiting client CLI");
                    break;
                }
                log.warn("Unknown command: {}", cmd);
            }
        }
    }

    private void runSync(MyDriveClient client) {
        try {
            client.runSingleSync();
            log.info("sync finished");
        } catch (Exception e) {
            log.error("sync failed", e);
        }
    }

    private void showConfig(ClientConfig config) {
        log.info("client.id={} client.dir={} server={}:{} max.connections={} transfer.mode={} metrics.csv.path={}",
                config.clientId(), config.clientDir(), config.serverIp(), config.serverPort(),
                config.maxConnections(), config.transferMode(), config.metricsCsvPath());
    }
}
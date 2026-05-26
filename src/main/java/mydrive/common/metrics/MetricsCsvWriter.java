package mydrive.common.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class MetricsCsvWriter {
    private static final String HEADER =
            "sync_id,file_name,size_bytes,duration_ms,throughput_mbps,transfer_mode,max_connections,started_at_epoch_ms,finished_at_epoch_ms";

    private final Path csvPath;

    public MetricsCsvWriter(Path csvPath) {
        this.csvPath = csvPath;
    }

    public synchronized void append(TransferMetricRecord record) throws IOException {
        ensureHeader();
        String line = String.join(",",
                esc(record.syncId()),
                esc(record.fileName()),
                Long.toString(record.sizeBytes()),
                Long.toString(record.durationMs()),
                String.format(Locale.US, "%.3f", record.throughputMbps()),
                esc(record.transferMode()),
                Integer.toString(record.maxConnections()),
                Long.toString(record.startedAtEpochMs()),
                Long.toString(record.finishedAtEpochMs())
        );
        Files.writeString(csvPath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void ensureHeader() throws IOException {
        if (Files.notExists(csvPath)) {
            Path parent = csvPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(csvPath, HEADER + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
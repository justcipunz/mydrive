package mydrive.common.metrics;

public record TransferMetricRecord(
        String syncId,
        String fileName,
        long sizeBytes,
        long durationMs,
        String transferMode,
        int maxConnections,
        long startedAtEpochMs,
        long finishedAtEpochMs
) {
    public double throughputMbps() {
        if (durationMs <= 0) {
            return 0.0;
        }
        double seconds = durationMs / 1000.0;
        return (sizeBytes * 8.0) / (seconds * 1_000_000.0);
    }
}
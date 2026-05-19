package mydrive.server.control;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mydrive.common.protocol.messages.FileMetadata;

final class ServerDiffUtil {
    private ServerDiffUtil() {
    }

    static DiffResult diff(List<FileMetadata> clientFiles, List<FileMetadata> serverFiles) {
        Map<String, FileMetadata> serverMap = new HashMap<>();
        for (FileMetadata f : serverFiles) {
            serverMap.put(f.name(), f);
        }

        List<FileMetadata> required = clientFiles.stream()
                .filter(clientFile -> isMissingOrChanged(clientFile, serverMap.get(clientFile.name())))
                .toList();

        Set<String> clientNames = clientFiles.stream().map(FileMetadata::name).collect(Collectors.toSet());
        List<String> extra = serverFiles.stream().map(FileMetadata::name)
                .filter(serverName -> !clientNames.contains(serverName))
                .toList();

        return new DiffResult(required, extra);
    }

    static boolean isMissingOrChanged(FileMetadata clientFile, FileMetadata serverFile) {
        if (serverFile == null) {
            return true;
        }
        if (clientFile.sizeBytes() != serverFile.sizeBytes()) {
            return true;
        }
        return !clientFile.checksumHex().equals(serverFile.checksumHex());
    }

    record DiffResult(List<FileMetadata> requiredFiles, List<String> extraServerFiles) {
    }
}
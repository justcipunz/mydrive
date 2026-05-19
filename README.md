# MyDrive HW4 (Java 17 + Netty)

## Run
1. Build:
```powershell
mvn -q -DskipTests package
```
2. Start server:
```powershell
mvn -q -DskipTests "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" "-Dexec.mainClass=mydrive.server.ServerMain"
```
3. Start client:
```powershell
mvn -q -DskipTests "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" "-Dexec.mainClass=mydrive.client.ClientMain"
```

Client commands: `sync`, `show-config`, `show-metrics-path`, `exit`.

## Config
- Client: `src/main/resources/client.properties`
- Server: `src/main/resources/server.properties`

If `client.id` is empty, client generates stable id into `client.id.file`.

## Metrics
After each successful sync, file-level transfer metrics are appended to:
- `metrics/transfer-metrics.csv`
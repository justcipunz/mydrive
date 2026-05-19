# MyDrive

MyDrive — клиент-серверное файловое хранилище на TCP.

Клиент синхронизирует локальную директорию с сервером. Сервер хранит файлы каждого клиента в отдельной папке `storage/{clientId}`.

## Что реализовано

- подключение клиента к серверу по TCP;
- постоянный `client.id`;
- отправка списка файлов клиента на сервер;
- сравнение файлов по имени, размеру и SHA-256;
- передача только недостающих или измененных файлов;
- удаление лишних файлов на сервере;
- точная копия клиентской директории на сервере после синхронизации;
- параллельная передача файлов через несколько TCP-соединений;
- режимы передачи `NON_DMA` и `DMA`;
- сбор метрик передачи в CSV;
- собственный протокол поверх TCP с framing.

## Стек

- Java 17;
- Maven;
- Netty;
- Jackson;
- SLF4J;
- JUnit 5.

## Структура проекта

```text
src/main/java/mydrive
├── client   - клиентская часть
├── server   - серверная часть
└── common   - общий код: протокол, checksum, метрики
````

Основные классы:

* `ClientMain` — запуск клиента;
* `ServerMain` — запуск сервера;
* `DirectoryScanner` — сканирование клиентской директории;
* `SyncCoordinator` — управляющий обмен клиента с сервером;
* `FileTransferClient` — передача файлов;
* `ClientSessionHandler` — обработка сообщений на сервере;
* `ProtocolEncoder` / `ProtocolDecoder` — framing сообщений поверх TCP;
* `ChecksumService` — расчет SHA-256;
* `MetricsCsvWriter` — запись метрик передачи.

## Конфигурация клиента

Файл:

```text
src/main/resources/client.properties
```

Пример:

```properties
client.id=
client.id.file=./.mydrive-client-id
client.dir=./client-data
server.ip=127.0.0.1
server.port=9000
max.connections=4
transfer.mode=NON_DMA
metrics.csv.path=./metrics/transfer-metrics.csv
```

Параметры:

* `client.id` — ID клиента. Если пустой, создается автоматически;
* `client.id.file` — файл для хранения постоянного ID;
* `client.dir` — директория для синхронизации;
* `server.ip` — IP сервера;
* `server.port` — порт сервера;
* `max.connections` — число одновременных подключений, от 1 до 32;
* `transfer.mode` — режим передачи: `NON_DMA` или `DMA`;
* `metrics.csv.path` — путь к CSV с метриками.

## Конфигурация сервера

Файл:

```text
src/main/resources/server.properties
```

Пример:

```properties
server.bind.ip=0.0.0.0
server.bind.port=9000
server.storage.root=./storage
```

## Запуск

Сборка проекта:

```powershell
mvn -q -DskipTests package
```

Запуск сервера:

```powershell
mvn -q -DskipTests "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" "-Dexec.mainClass=mydrive.server.ServerMain"
```

Запуск клиента:

```powershell
mvn -q -DskipTests "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" "-Dexec.mainClass=mydrive.client.ClientMain"
```

## Команды клиента

После запуска клиента доступны команды:

```text
sync
show-config
show-metrics-path
exit
```

Команда `sync` запускает синхронизацию клиентской директории с сервером.

## Протокол

Служебные сообщения передаются через собственный протокол поверх TCP.

Формат кадра:

```text
frameLength
messageType
requestId
payload
```

TCP не сохраняет границы сообщений, поэтому на стороне получателя используется `ProtocolDecoder`. Он читает длину кадра и ждет, пока придут все байты сообщения.

Служебные сообщения сериализуются через Jackson в массив байт.

## Режим NON_DMA

В режиме `NON_DMA` файл передается частями. Каждая часть отправляется как сообщение `FILE_TRANSFER_CHUNK`.

Сервер записывает части во временный файл и считает SHA-256. После завершения передачи сервер сравнивает размер и checksum.

## Режим DMA

В режиме `DMA` клиент сначала отправляет служебное сообщение `FILE_TRANSFER_START`.

После подтверждения сервером файл передается через Netty `DefaultFileRegion`.

Граница файла определяется по размеру из `FILE_TRANSFER_START`.

## Метрики

После успешной синхронизации метрики записываются в CSV:

```text
metrics/transfer-metrics.csv
```

В файл записываются:

* `sync_id`;
* `file_name`;
* `size_bytes`;
* `duration_ms`;
* `throughput_mbps`;
* `transfer_mode`;
* `max_connections`;
* `started_at_epoch_ms`;
* `finished_at_epoch_ms`.

## Тесты

Запуск тестов:

```powershell
mvn -q test
```

В тестах проверяются:

* расчет SHA-256;
* сканирование директории;
* framing TCP-сообщений;
* обработка partial TCP frames;
* сравнение файлов клиента и сервера;
* удаление лишних файлов.


# Hotel Staff Attendance Control

Individual Software Engineering II extraordinary project for hotel staff attendance.
The target covers continuous administration hours, split restaurant hours and
reception shifts, including overnight work.

## Current implementation

The Maven modules share an event model and preserve the existing text-file
storage. The tracking module can simulate a punch from the command line and
print its JSON message. The network services and user interfaces are pending.

| Module | Current contents | Intended responsibility |
| --- | --- | --- |
| `attendance-core` | Validated employee, building and event records; tracking types. | Shared domain types without infrastructure dependencies. |
| `tracking-service` | Runnable simulation with JSON configuration, input parser and console publisher. | Capture and publish attendance events. |
| `employee-service` | Maven module declaration. | Employee administration API. |
| `building-service` | Maven module declaration. | Building administration API. |
| `employee-admin` | Maven module declaration. | Protected employee administration application. |
| `building-admin` | Maven module declaration. | Protected building administration application. |
| `storer` | Storage port and migrated text-file adapters. | Consume tracking messages and persist events. |

`tracking-service` and `storer` both depend on `attendance-core`; neither depends
on the other. The admin applications have no Java dependency on service
implementations: their eventual connection will use documented APIs.

## Build and verification

Requirements: JDK 27 and Maven 3.9.x. All modules inherit the same Java release
from the root `pom.xml`. Set `JAVA_HOME` to the JDK installation and make Maven
available on `PATH`, or configure the equivalent Maven runner in IntelliJ IDEA.

Run from the repository root:

```shell
mvn test
mvn package
```

To verify only the tracking module and its upstream dependencies:

```shell
mvn -pl tracking-service -am test
```

Tests cover incomplete model data, valid event publication, preventing publication
after reading/parsing failures, propagating publication failures, preserving
append-only text storage across reopen, UTC partitioning and filesystem errors.
They also check the complete simulated path for both punch types and reject
invalid configuration or input without emitting an event. No broker or external
services are required.

## Run a simulated punch

In IntelliJ IDEA, run `software.ulpgc.attendance.tracking.infrastructure.Main`
using the `tracking-service` module and the repository root as its working
directory. Set the program arguments to:

```text
tracking-service/configuration.example.json employee-001;in
```

Use `employee-001;out` to simulate a departure. To run from PowerShell with the
default Maven cache, build from the repository root and use:

```powershell
mvn -pl tracking-service -am package
$trackingGson = Join-Path $env:USERPROFILE '.m2/repository/com/google/code/gson/gson/2.15.0/gson-2.15.0.jar'
$trackingClasspath = "tracking-service/target/classes;attendance-core/target/classes;$trackingGson"
java -cp $trackingClasspath software.ulpgc.attendance.tracking.infrastructure.Main tracking-service/configuration.example.json 'employee-001;in'
```

The example configuration supplies the building identifier. To try another
building, copy it to `tracking-service/configuration.local.json` and pass that
path instead. The example employee ID is supplied as device input, not loaded
from the employee service.

The output is one JSON line with the current timestamp. The console publisher
does not send it to a broker or store it. Invalid configuration or input
produces an error on standard error and exits with status 1.

## Backend structure

Shared model types are in
`software.ulpgc.attendance.application.domain.model` inside `attendance-core`.
Module-specific application ports/use cases are separated from infrastructure
adapters. The application depends on contracts; filesystem/device/broker details
belong to implementations outside the domain.

The current capture collaboration is:

```text
RegisterPresenceUseCase.execute()
    -> DeviceReader.read()
    -> TrackingEventParser.parse(input)
    -> EventPublisher.publish(event)
```

The reader and publisher may report `IOException`. A rejected parse is not
published, and publication errors propagate to the caller. This use case
coordinates capture only; employee lookup, schedule validation and authoritative
attendance-state rules are not implemented yet.

## Tracking event contract

```java
public record TrackingEvent(Instant ts, String employee, String building, TrackingType type)
```

- `ts`: event instant, independent of the workstation timezone.
- `employee`: employee identifier, not a complete employee object.
- `building`: building identifier, replacing the earlier `Place` reference.
- `type`: `ENTRY` or `EXIT` in the Java domain.

Constructors reject missing timestamps/types and null, empty or blank identifiers.
Employee and building records also require a nonblank name. These checks do not
prove that an identifier exists in a master service.

The console publisher maps `ENTRY` to `in` and `EXIT` to `out`. Example output:

```json
{"ts":"2026-11-05T09:00:00Z","employee":"employee-001","building":"hotel-001","type":"in"}
```

Gson handles JSON in the infrastructure layer. The console output uses the target
message fields; the broker adapter, event identity, source metadata and delivery
semantics remain pending.

## Existing text storage

`FileEventStore` has moved to `storer`. `EventStore.save(event)` delegates formatting
to `EventWriter.writeEvent(event, output)`. A root supplied as `new File("store")`
uses the current working directory and writes daily UTC files such as:

```text
store/2026/20261105.log
```

Each record retains the previous semicolon-separated order:

```text
employee-001;hotel-001;2026-11-05T09:00:00Z;ENTRY
```

Folders are created when needed; subsequent saves append a line rather than
truncating the file. The storage operation closes its own file stream. The writer
flushes but leaves the caller-owned stream open.

This is compatibility with the previous increment, not the future JSONL datalake.
The adapter does not validate arbitrary delimiter characters in identifiers,
coordinate concurrent writers or guarantee transactional/physical disk durability.

## Planned system architecture

```text
tracking-service -> ActiveMQ -> storer -> datalake
employee-admin -> employee-service API
building-admin -> building-service API
```

The future datalake will use `in/YYYYMM/DD.jsonl` and `out/YYYYMM/DD.jsonl`, with
one JSON event per line. The current semicolon serializer must be replaced or
migrated explicitly; changing the extension alone does not convert the data.

Service URLs, broker credentials, building identity and runtime storage settings
will be loaded from external JSON/YAML configuration. Real credentials and runtime
datalake files must not be committed. ActiveMQ, HTTP API servers, administrative
authentication, Docker and Nginx routing are not implemented yet.

## Remaining delivery requirements

The final application must include three differentiated UI modules for attendance,
history/incidence queries and schedule definition, in addition to employee/building
administration. Domain work still includes dated assignments, the three hotel
schedule cases, presence status, hours/deviation and meaningful error handling.

The final README will add actual startup commands/configuration, HTTP and message
contracts, implemented architecture, fictional seed data, all three required
screenshots, the chosen personal contribution and verified limitations.

# Hotel Staff Attendance Control

Individual Software Engineering II extraordinary project for hotel staff attendance.
The target covers continuous administration hours, split restaurant hours and
reception shifts, including overnight work.

## Current implementation

The Maven modules share an event model and preserve the existing text-file
storage. The tracking module can simulate a punch from the command line and
print its JSON message. The employee API, its web administration app and the
building API are available locally. Building administration, attendance
interfaces and broker integration are pending.

| Module | Current contents | Intended responsibility |
| --- | --- | --- |
| `attendance-core` | Validated employee, building and event records; tracking types. | Shared domain types without infrastructure dependencies. |
| `tracking-service` | Runnable simulation with JSON configuration, input parser and console publisher. | Capture and publish attendance events. |
| `employee-service` | Local HTTP API for employee registration, lookup, listing and deactivation. | Employee administration API. |
| `building-service` | Local HTTP API for building registration, lookup, listing and deactivation. | Building administration API. |
| `employee-admin` | Web page for listing, registering and deactivating employees through a Java server. | Protected employee administration application. |
| `building-admin` | Maven module declaration. | Protected building administration application. |
| `storer` | Storage port and migrated text-file adapters. | Consume tracking messages and persist events. |

`tracking-service` and `storer` both depend on `attendance-core`; neither depends
on the other. The employee admin app communicates with the service through
its HTTP API and does not depend on its implementation.

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

The employee service uses Javalin to route HTTP requests to `EmployeeApi`.
`EmployeeDirectory` applies the registration and deactivation rules through an
`EmployeeRepository` port. `JsonEmployeeRepository` persists snapshots to a
local file. The application and domain do not depend on Javalin or Gson.

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

## Run the employee service

Build from the repository root. In PowerShell, set a local development token and
start the executable JAR:

```powershell
mvn -pl employee-service -am package
$env:ATTENDANCE_ADMIN_TOKEN = Read-Host -MaskInput 'Local employee API token'
java -jar employee-service/target/employee-service-1.0-SNAPSHOT-app.jar 8081 employee-service/data/employees.json
```

The arguments select the port and data file. The service binds to
`127.0.0.1`. All employee requests require
`Authorization: Bearer <token>` with the value configured at startup. Keep the
token out of source control and use fictional employee data. Stop the service
with Ctrl+C.

| Method and path | Request | Success |
| --- | --- | --- |
| `POST /api/employees` | JSON with string fields `id` and `name` | `201` with the new record and a `Location` header |
| `GET /api/employees` | No body | `200` with all records, including inactive employees |
| `GET /api/employees/{id}` | No body | `200` with the requested record |
| `POST /api/employees/{id}/deactivate` | No body | `200` with the inactive record |

Example registration body:

```json
{"id":"employee-001","name":"Test Employee"}
```

Responses include `id`, `name`, `active`, `registeredAt` and
`deactivatedAt`. The timestamps use UTC, and `deactivatedAt` is null for
active employees. Deactivation keeps the record queryable and preserves the
first deactivation timestamp if repeated. An existing identifier cannot be
registered again, even after deactivation.

Errors include `400` for invalid input, `401` for missing or invalid tokens,
`404` for unknown employees, `409` for duplicate IDs, and `500` if a change
cannot be saved. These application errors return a JSON `code` and `message`.

The JSON file is loaded at startup. Changes are written to a temporary file and
replace the previous snapshot atomically; the in-memory state changes only after
a successful write. Loading rejects invalid field types, incomplete records,
duplicate identifiers and deactivation dates before registration without
changing the file. Use one service instance per file on a filesystem that
supports atomic moves. The integration tests exercise the HTTP API and restart
it against the same temporary data file.

## Run employee administration

Start the employee service first. Then open a second PowerShell window in the
repository root and run:

```powershell
mvn -pl employee-admin package
$env:ATTENDANCE_ADMIN_TOKEN = Read-Host -MaskInput 'Same token used by employee-service'
$env:ATTENDANCE_ADMIN_WEB_PASSWORD = Read-Host -MaskInput 'Password for the web user admin'
java -jar employee-admin/target/employee-admin-1.0-SNAPSHOT-app.jar http://127.0.0.1:8081 8082
```

The arguments are the employee service base URL and the web server port. Open
`http://127.0.0.1:8082/` and sign in as `admin` with the configured web password.
The API bearer token is used only by the Java processes.

In IntelliJ IDEA, run
`software.ulpgc.attendance.employeeadmin.infrastructure.Main` in the
`employee-admin` module. Put `http://127.0.0.1:8081 8082` in **Program
arguments** and set both variables in **Environment variables**.

The page lists active and inactive employees and their registration and
deactivation dates. Fill in **Employee ID** and **Full name** to register an
employee. Use **Deactivate** on an active row to deactivate it while keeping
its record. The search field filters the rows currently displayed.

`EmployeeAdminConfiguration` reads the launch arguments and environment
variables; `Main` wires the components and manages startup and shutdown.
`EmployeeAdminServer` manages the local HTTP server, authentication and a pool
of four request workers, which it shuts down when closed. A slow API request
does not hold up all other requests while another worker is available.
`EmployeeAdminHandler` routes HTTP requests and sends responses;
`EmployeeForm` decodes and bounds form input. `EmployeeAdminController`
coordinates listing, registration and deactivation through `EmployeeClient`,
including form validation, errors and redirects. `HttpEmployeeClient`
implements that port using the employee API.
`EmployeeAdminResources` loads the static resources once at startup;
`EmployeeAdminPage` renders the HTML templates and escapes employee data.
If registration fails, the submitted ID and name stay in the form so
they can be corrected. A duplicate registration keeps its conflict status,
and an unavailable directory is shown without zero counts.
JavaScript only filters displayed rows; the forms and server operations work
with JavaScript disabled. Tests cover form submission, authentication, rejected
form tokens, escaping, invalid API responses and independent requests during
a slow directory lookup. Controller tests use a substitute `EmployeeClient`
without HTTP to verify failure messages, retained input and interruption.

## Run the building service

Build from the repository root with `mvn -pl building-service -am package`.
In PowerShell, set the local administration token and start the executable JAR:

```powershell
$env:ATTENDANCE_ADMIN_TOKEN = Read-Host -MaskInput 'Local building API token'
java -jar building-service/target/building-service-1.0-SNAPSHOT-app.jar 8083 building-service/data/buildings.json
```

In IntelliJ IDEA, run `software.ulpgc.attendance.building.infrastructure.Main`
in `building-service`. Set the working directory to the repository root, pass
`8083 building-service/data/buildings.json` as the program arguments and set
`ATTENDANCE_ADMIN_TOKEN` in the environment variables.

The service listens on `127.0.0.1` and requires
`Authorization: Bearer <token>` on every request.

| Method and path | Request | Success |
| --- | --- | --- |
| `POST /api/buildings` | JSON with string fields `id` and `name` | `201` with the record and its `Location` |
| `GET /api/buildings` | No body | `200` with all records, including inactive buildings |
| `GET /api/buildings/{id}` | No body | `200` with the requested record |
| `POST /api/buildings/{id}/deactivate` | No body | `200` with the inactive record |

A building identifies a physical property. For example, send
`{"id":"hotel-001","name":"Test Hotel"}` to register a fictional property.
The ID starts with an ASCII letter or digit and may also contain dots,
underscores and hyphens. Names must be nonblank.

Each record contains `id`, `name`, `active`, `registeredAt` and
`deactivatedAt`. Timestamps are UTC. The deactivation timestamp is null
while the building is active. An inactive building remains available for
historical lookup. Repeated deactivation preserves the first timestamp
and registration never reuses an existing ID.

Errors return JSON with `code` and `message`. The API uses `400` for invalid
input, `401` for missing or invalid tokens, `404` for an unknown building,
`409` for a duplicate ID and `500` for a failed write. The service loads
and atomically replaces a JSON snapshot using one service instance per data
file. A rejected operation leaves the last saved records intact.

Verify the module with `mvn -pl building-service -am test`. The tests cover
registration, lookup, deactivation, reopening the data file, rejected
requests and storage failures.

## Existing text storage

`FileEventStore` has moved to `storer`. `EventStore.save(event)` formats and
validates the event through `EventWriter` before opening the file. A root supplied as `new File("store")`
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
The adapter rejects semicolons, carriage returns and newlines in employee and
building identifiers. A rejected event does not create or modify a log file.
The adapter does not coordinate concurrent writers or guarantee transactional
or physical disk durability.

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
datalake files must not be committed. ActiveMQ, the attendance API, building
administration, Docker and Nginx routing are pending. Tracking still uses
its configured building ID directly. Validation through the building API
is a separate integration step.

## Remaining delivery requirements

The final application must include three differentiated UI modules for attendance,
history/incidence queries and schedule definition, in addition to employee/building
administration. Domain work still includes dated assignments, the three hotel
schedule cases, presence status, hours/deviation and meaningful error handling.

The final README will add actual startup commands/configuration, HTTP and message
contracts, implemented architecture, fictional seed data, all three required
screenshots, the chosen personal contribution and verified limitations.

# DumpPorts

A "modern", visual tool to read and display network socket statistics using socket statistics (`ss`) command with a JavaFX graphical interface.

## Features

- Real-time socket statistics visualization
- Filter sockets by protocol (TCP, UDP, RAW, SCTP)
- Detailed socket information display (addresses, ports, process names)
- Shows complete binary paths
- Automatic data refresh capability
- Export functionality (CSV and JSON formats)
- Clean and intuitive user interface
- Optional root mode for full visibility of all users' processes (PolicyKit)
- Cross-platform compatibility (Linux, Windows with WSL)

## Requirements

- Java 25 or higher
- Maven 3.6 or higher
- Linux/Unix system with `ss` command available
- JavaFX
- PolicyKit (`pkexec`) and a running authentication agent for root mode

## Building the Project

```bash
mvn clean package
```

## Running the Application

### Using Maven

```bash
mvn javafx:run
```

> **JDK 25 required.** The project targets Java 25 and JavaFX 25, so Maven must
> run on a JDK 25 (or newer). If Maven is using an older JDK you will see an
> error like `Unsupported major.minor version 67.0`. Set `JAVA_HOME` to a
> JDK 25 installation before running Maven:
>
> ```bash
> export JAVA_HOME=/path/to/jdk-25
> mvn javafx:run
> ```
>
> The build fails fast with a clear message when the JDK is too old.

### Using JAR

```bash
java -jar target/dumpports-{version}.jar
```

## Usage Guide

### Loading Socket Data

1. Launch the application
2. Click the **Refresh** button to load current socket statistics
3. The table will display all network sockets with detailed information

### Root Mode

By default the app runs in **User Mode** and can only resolve process names for
your own sockets. On startup (and at any time through the **Run as Root**
button) DumpPorts can request root privileges so that processes of every user
are visible.

1. On startup, confirm the "Run DumpPorts with root privileges?" dialog
2. Authenticate with your system password in the PolicyKit (`pkexec`) prompt
3. The mode label switches to **Root Mode** and socket queries now show all
   processes, including their executable paths

A single privileged helper is started once and kept alive, so auto-refresh does
not ask for the password again. If you decline or authentication fails, the app
stays in **User Mode**, the mode label shows this, and the **Run as Root**
button remains available to try again later. If PolicyKit is not available the
app simply runs in User Mode.

### Real-time Auto Refresh

1. Toggle **Auto Refresh** to enable periodic updates
2. Set the **Interval (s)** spinner to your preferred cadence (2–300 seconds)
3. The app refreshes in the background without blocking the UI
4. Current protocol filter is preserved across refreshes

### Filtering by Protocol

1. Use the **Protocol Filter** dropdown menu at the top
2. Select a protocol (TCP, UDP, RAW, SCTP, or All)
3. The table will automatically update to show only sockets matching the selected protocol

### Exporting Data

#### Export to CSV
1. Load socket data using the Refresh button
2. Click the **Export to CSV** button
3. Choose a location and filename in the file dialog
4. The data will be exported in CSV format with all socket information

#### Export to JSON
1. Load socket data using the Refresh button
2. Click the **Export to JSON** button
3. Choose a location and filename in the file dialog
4. The data will be exported in JSON format with metadata and timestamps

## Dependencies

- JavaFX 25 - GUI framework
- SLF4J - Logging API
- Logback - Logging implementation
- Gson - JSON processing
- JUnit 4 - Testing framework
- Mockito - Mocking framework for testing

## Configuration

All logging configuration is managed through `logback.xml` in the resources folder. Logs are written to both console and file (`logs/dumpports.log`).

## Development Guidelines

- Follow Java naming conventions (CamelCase for classes, camelCase for methods)
- Use Javadoc for public APIs
- Write unit tests for new functionality
- Use English for all code, comments, and documentation
- Avoid code duplication (DRY principle)
- Implement proper exception handling

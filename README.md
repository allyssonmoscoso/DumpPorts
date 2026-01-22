# DumpPorts

A "modern", visual tool to read and display network socket statistics using socket statistics (`ss`) command with a JavaFX graphical interface.

## Features

- Real-time socket statistics visualization
- Filter sockets by protocol (TCP, UDP, RAW, SCTP)
- Detailed socket information display (addresses, ports, process names)
- Automatic data refresh capability
- Export functionality (CSV and JSON formats)
- Clean and intuitive user interface
- Cross-platform compatibility (Linux, Windows with WSL)

## Requirements

- Java 25 or higher
- Maven 3.6 or higher
- Linux/Unix system with `ss` command available
- JavaFX

## Building the Project

```bash
mvn clean package
```

## Running the Application

### Using Maven

```bash
mvn javafx:run
```

### Using JAR

```bash
java -jar target/dumpports-{version}.jar
```

## Usage Guide

### Loading Socket Data

1. Launch the application
2. Click the **Refresh** button to load current socket statistics
3. The table will display all network sockets with detailed information

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

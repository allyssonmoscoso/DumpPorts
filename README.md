# DumpPorts

A "modern", visual tool to read and display network socket statistics using socket statistics (`ss`) command with a JavaFX graphical interface.

## Features

- Real-time socket statistics visualization
- Filter sockets by protocol (TCP, UDP, RAW, SCTP)
- Detailed socket information display (addresses, ports, process names)
- Automatic data refresh capability
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

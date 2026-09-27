package com.dumpports.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;

/**
 * Service class for reading socket statistics from the system using the 'ss' command.
 * Parses output and converts it into Socket objects.
 *
 * <p>Data can be retrieved directly (user mode) or through a privileged helper
 * (root mode) managed by {@link PrivilegeManager}.</p>
 */
public class SocketStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(SocketStatisticsService.class);

    private final PrivilegeManager privilegeManager;

    /**
     * Creates a service in user mode. Kept for backwards compatibility and tests.
     */
    public SocketStatisticsService() {
        this(new PrivilegeManager());
    }

    /**
     * Creates a service bound to the given privilege manager.
     *
     * @param privilegeManager shared privilege state
     */
    public SocketStatisticsService(PrivilegeManager privilegeManager) {
        this.privilegeManager = privilegeManager;
    }

    /**
     * Executes the 'ss' command and retrieves all network sockets.
     * When root mode is active the query is delegated to the privileged helper,
     * otherwise it runs directly with the current user's permissions.
     *
     * @return List of Socket objects containing parsed statistics
     */
    public List<Socket> getSocketStatistics() {
        List<String> lines;
        Map<String, String> exeByPid;

        PrivilegedSocketHelper helper = privilegeManager.getHelper();
        if (helper != null) {
            try {
                PrivilegedSocketHelper.SocketData data = helper.dump();
                lines = data.ssLines();
                exeByPid = data.exeByPid();
            } catch (Exception e) {
                logger.error("Error retrieving socket statistics from privileged helper", e);
                privilegeManager.reportHelperFailure();
                lines = runLocalSs();
                exeByPid = Map.of();
            }
        } else {
            lines = runLocalSs();
            exeByPid = Map.of();
        }

        List<Socket> sockets = new ArrayList<>();
        for (String line : lines) {
            Socket socket = parseSocketLine(line, exeByPid);
            if (socket != null) {
                sockets.add(socket);
            }
        }

        logger.info("Retrieved {} socket statistics (root mode: {})", sockets.size(), privilegeManager.isRootMode());
        return sockets;
    }

    /**
     * Runs the local {@code ss} command with the current user's permissions.
     *
     * @return non-blank output lines, or an empty list on failure
     */
    private List<String> runLocalSs() {
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ss", "-tunapH");
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        lines.add(line);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logger.error("Error retrieving local socket statistics", e);
        }
        return lines;
    }

    /**
     * Retrieves socket statistics filtered by protocol.
     *
     * @param protocol Protocol to filter (tcp, udp, etc.)
     * @return List of Socket objects matching the protocol
     */
    public List<Socket> getSocketsByProtocol(String protocol) {
        List<Socket> allSockets = getSocketStatistics();
        return allSockets.stream()
                .filter(socket -> socket.getProtocol().equalsIgnoreCase(protocol))
                .toList();
    }

    /**
     * Parses a single line from ss command output.
     * Format: Netid State Recv-Q Send-Q Local-Address:Port Peer-Address:Port Process
     *
     * @param line Raw output line from ss command
     * @param exeByPid Optional map of PID to executable path (root mode)
     * @return Parsed Socket object or null if parsing fails
     */
    Socket parseSocketLine(String line, Map<String, String> exeByPid) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            String[] parts = line.trim().split("\\s+", 7); // Split into max 7 parts to preserve process field
            if (parts.length < 6) {
                return null;
            }

            Socket socket = new Socket();
            
            // Column 0: Netid (protocol)
            socket.setProtocol(parts[0]);
            
            // Column 1: State
            socket.setState(parts[1]);
            
            // Column 2: Recv-Q
            socket.setRecvQueue(parts[2]);
            
            // Column 3: Send-Q
            socket.setSendQueue(parts[3]);
            
            // Column 4: Local Address:Port
            parseAddressPort(parts[4], socket, true);
            
            // Column 5: Peer Address:Port
            parseAddressPort(parts[5], socket, false);
            
            // Column 6: Process (optional)
            if (parts.length > 6 && !parts[6].isEmpty()) {
                parseProcessInfo(parts[6], socket, exeByPid);
            }

            return socket;
        } catch (Exception e) {
            logger.debug("Error parsing socket line: {}", line, e);
            return null;
        }
    }

    /**
     * Parses address and port from format "address:port" or "[ipv6]:port".
     *
     * @param addressPort The address:port string
     * @param socket The socket object to update
     * @param isLocal True if this is a local address, false for remote
     */
    private void parseAddressPort(String addressPort, Socket socket, boolean isLocal) {
        if (addressPort == null || addressPort.equals("*") || addressPort.isEmpty()) {
            if (isLocal) {
                socket.setLocalAddress("*");
                socket.setLocalPort("*");
            } else {
                socket.setRemoteAddress("*");
                socket.setRemotePort("*");
            }
            return;
        }

        String address;
        String port;

        // Handle IPv6 addresses [::]:port
        if (addressPort.startsWith("[")) {
            int closeBracket = addressPort.indexOf("]");
            if (closeBracket != -1) {
                address = addressPort.substring(1, closeBracket);
                port = addressPort.substring(closeBracket + 2); // Skip "]:"
            } else {
                address = addressPort;
                port = "*";
            }
        } else {
            // Handle IPv4 or hostname:port
            int lastColon = addressPort.lastIndexOf(":");
            if (lastColon != -1) {
                address = addressPort.substring(0, lastColon);
                port = addressPort.substring(lastColon + 1);
            } else {
                address = addressPort;
                port = "*";
            }
        }

        if (isLocal) {
            socket.setLocalAddress(address);
            socket.setLocalPort(port);
        } else {
            socket.setRemoteAddress(address);
            socket.setRemotePort(port);
        }
    }

    /**
     * Parses process information from ss output.
     * Format: users:(("process_name",pid=1234,fd=5))
     * The executable path is taken from the privileged helper when available,
     * otherwise it is resolved locally from /proc/pid/exe.
     *
     * @param processField The process field from ss output
     * @param socket The socket object to update
     * @param exeByPid Optional map of PID to executable path (root mode)
     */
    private void parseProcessInfo(String processField, Socket socket, Map<String, String> exeByPid) {
        try {
            // Extract process name from users:(("name",pid=123,fd=4))
            if (processField.contains("((\"")) {
                int startQuote = processField.indexOf("((\"") + 3;
                int endQuote = processField.indexOf("\"", startQuote);
                if (endQuote > startQuote) {
                    String processName = processField.substring(startQuote, endQuote);
                    socket.setProcess(processName);
                }
            }

            // Extract PID from pid=1234
            String pid = null;
            if (processField.contains("pid=")) {
                int pidStart = processField.indexOf("pid=") + 4;
                int pidEnd = processField.indexOf(",", pidStart);
                if (pidEnd == -1) {
                    pidEnd = processField.indexOf(")", pidStart);
                }
                if (pidEnd > pidStart) {
                    pid = processField.substring(pidStart, pidEnd);
                    socket.setPid(pid);

                    // Prefer the path resolved by the privileged helper.
                    String execPath = exeByPid == null ? null : exeByPid.get(pid);
                    if (execPath == null || execPath.isEmpty()) {
                        execPath = getExecutablePath(pid);
                    }
                    if (execPath != null && !execPath.isEmpty()) {
                        socket.setExecutablePath(execPath);
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Error parsing process info: {}", processField, e);
        }
    }

    /**
     * Retrieves the full executable path for a given PID by reading /proc/pid/exe symlink.
     *
     * @param pid Process ID
     * @return Full path to the executable, or null if not accessible
     */
    private String getExecutablePath(String pid) {
        if (pid == null || pid.isEmpty()) {
            return null;
        }

        try {
            Path exePath = Paths.get("/proc", pid, "exe");
            if (Files.exists(exePath)) {
                Path realPath = Files.readSymbolicLink(exePath);
                return realPath.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            logger.trace("Could not read executable path for PID {}: {}", pid, e.getMessage());
        }

        return null;
    }
}

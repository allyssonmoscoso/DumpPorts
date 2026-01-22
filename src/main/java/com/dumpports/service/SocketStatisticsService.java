package com.dumpports.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;

/**
 * Service class for reading socket statistics from the system using the 'ss' command.
 * Parses output and converts it into Socket objects.
 */
public class SocketStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(SocketStatisticsService.class);

    /**
     * Executes the 'ss' command and retrieves all network sockets.
     * Requires appropriate system permissions.
     *
     * @return List of Socket objects containing parsed statistics
     */
    public List<Socket> getSocketStatistics() {
        List<Socket> sockets = new ArrayList<>();
        
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ss", "-tunap");
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            boolean headerSkipped = false;
            
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                Socket socket = parseSocketLine(line);
                if (socket != null) {
                    sockets.add(socket);
                }
            }
            
            process.waitFor();
            logger.info("Retrieved {} socket statistics", sockets.size());
            
        } catch (Exception e) {
            logger.error("Error retrieving socket statistics", e);
        }
        
        return sockets;
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
     *
     * @param line Raw output line from ss command
     * @return Parsed Socket object or null if parsing fails
     */
    private Socket parseSocketLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) {
                return null;
            }

            Socket socket = new Socket();
            socket.setProtocol(parts[0]);
            socket.setRecvQueue(parts[1]);
            socket.setSendQueue(parts[2]);
            socket.setLocalAddress(parts[3]);
            socket.setRemoteAddress(parts[4]);
            socket.setState(parts[5]);

            // Parse local address and port
            String[] localParts = parts[3].split(":");
            if (localParts.length >= 2) {
                socket.setLocalAddress(localParts[0]);
                socket.setLocalPort(localParts[localParts.length - 1]);
            }

            // Parse remote address and port
            String[] remoteParts = parts[4].split(":");
            if (remoteParts.length >= 2) {
                socket.setRemoteAddress(remoteParts[0]);
                socket.setRemotePort(remoteParts[remoteParts.length - 1]);
            }

            // Extract PID and process name if available (typically in parts[6])
            if (parts.length > 6) {
                String pidProcess = parts[6];
                if (pidProcess.contains("(")) {
                    String[] pidParts = pidProcess.split("[()]");
                    if (pidParts.length >= 2) {
                        socket.setPid(pidParts[0]);
                        socket.setProcess(pidParts[1]);
                    }
                }
            }

            return socket;
        } catch (Exception e) {
            logger.debug("Error parsing socket line: {}", line, e);
            return null;
        }
    }
}

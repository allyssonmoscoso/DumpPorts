package com.dumpports.service;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import com.dumpports.model.Socket;

/**
 * Unit tests for SocketStatisticsService class.
 */
public class SocketStatisticsServiceTest {

    private SocketStatisticsService service;

    @Before
    public void setUp() {
        service = new SocketStatisticsService();
    }

    @Test
    public void testGetSocketStatistics() {
        List<Socket> sockets = service.getSocketStatistics();
        
        assertNotNull("Sockets list should not be null", sockets);
        
        // Log the results for manual verification
        System.out.println("Retrieved " + sockets.size() + " sockets");
        
        // Print first few sockets with process info
        int count = 0;
        for (Socket socket : sockets) {
            if (socket.getProcess() != null && !socket.getProcess().isEmpty()) {
                System.out.println("Socket with process: " + socket.getProtocol() + 
                        " - Process: " + socket.getProcess() + 
                        " - PID: " + socket.getPid() +
                        " - Executable: " + socket.getExecutablePath() +
                        " - Local: " + socket.getLocalAddress() + ":" + socket.getLocalPort());
                count++;
                if (count >= 5) break;
            }
        }
        
        System.out.println("Found " + count + " sockets with process information");
    }

    @Test
    public void testGetSocketsByProtocol() {
        List<Socket> tcpSockets = service.getSocketsByProtocol("tcp");
        List<Socket> udpSockets = service.getSocketsByProtocol("udp");
        
        assertNotNull("TCP sockets list should not be null", tcpSockets);
        assertNotNull("UDP sockets list should not be null", udpSockets);
        
        // Verify all returned sockets match the requested protocol
        for (Socket socket : tcpSockets) {
            assertTrue("Socket protocol should be tcp", 
                    socket.getProtocol().equalsIgnoreCase("tcp"));
        }
        
        for (Socket socket : udpSockets) {
            assertTrue("Socket protocol should be udp", 
                    socket.getProtocol().equalsIgnoreCase("udp"));
        }
        
        System.out.println("TCP sockets: " + tcpSockets.size());
        System.out.println("UDP sockets: " + udpSockets.size());
    }
}

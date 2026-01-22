package com.dumpports.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Socket model class.
 */
public class SocketTest {

    private Socket socket;

    @Before
    public void setUp() {
        socket = new Socket("tcp", "LISTEN", "127.0.0.1", "8080", "0.0.0.0", "0");
    }

    @Test
    public void testSocketCreation() {
        assertNotNull(socket);
        assertEquals("tcp", socket.getProtocol());
        assertEquals("LISTEN", socket.getState());
        assertEquals("127.0.0.1", socket.getLocalAddress());
        assertEquals("8080", socket.getLocalPort());
    }

    @Test
    public void testSocketSetters() {
        socket.setPid("1234");
        socket.setProcess("java");
        socket.setRecvQueue("0");
        socket.setSendQueue("0");

        assertEquals("1234", socket.getPid());
        assertEquals("java", socket.getProcess());
        assertEquals("0", socket.getRecvQueue());
        assertEquals("0", socket.getSendQueue());
    }

    @Test
    public void testSocketToString() {
        socket.setProcess("nginx");
        String result = socket.toString();
        assertTrue(result.contains("tcp"));
        assertTrue(result.contains("nginx"));
    }
}

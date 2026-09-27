package com.dumpports.service;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Unit tests for the privileged helper response protocol.
 */
public class PrivilegedSocketHelperTest {

    @Test
    public void testParseResponseSplitsSections() throws Exception {
        String dump = String.join("\n",
                "tcp ESTAB 0 0 127.0.0.1:8080 127.0.0.1:5000 users:((\"java\",pid=123,fd=5))",
                "udp UNCONN 0 0 0.0.0.0:5353 0.0.0.0:* users:((\"avahi\",pid=456,fd=7))",
                "\u001eEXE\u001e",
                "123\t/usr/bin/java",
                "456\t/usr/sbin/avahi-daemon",
                "\u001eEND\u001e");

        PrivilegedSocketHelper.SocketData data =
                PrivilegedSocketHelper.parseResponse(new BufferedReader(new StringReader(dump)));

        assertEquals(2, data.ssLines().size());
        assertEquals("/usr/bin/java", data.exeByPid().get("123"));
        assertEquals("/usr/sbin/avahi-daemon", data.exeByPid().get("456"));
    }

    @Test
    public void testParseResponseWithoutSockets() throws Exception {
        String dump = "\u001eEXE\u001e\n\u001eEND\u001e";

        PrivilegedSocketHelper.SocketData data =
                PrivilegedSocketHelper.parseResponse(new BufferedReader(new StringReader(dump)));

        assertTrue(data.ssLines().isEmpty());
        assertTrue(data.exeByPid().isEmpty());
    }

    @Test(expected = java.io.IOException.class)
    public void testParseResponseTruncatedThrows() throws Exception {
        String dump = "tcp ESTAB 0 0 1.2.3.4:1 5.6.7.8:2 users:((\"x\",pid=1,fd=1))";

        PrivilegedSocketHelper.parseResponse(new BufferedReader(new StringReader(dump)));
    }
}

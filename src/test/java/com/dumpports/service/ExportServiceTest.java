package com.dumpports.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import com.dumpports.model.Socket;

/**
 * Unit tests for ExportService class.
 */
public class ExportServiceTest {

    private ExportService exportService;
    private List<Socket> testSockets;
    private File tempDir;

    @Before
    public void setUp() {
        exportService = new ExportService();
        testSockets = new ArrayList<>();

        Socket socket1 = new Socket("tcp", "LISTEN", "127.0.0.1", "8080", "0.0.0.0", "0");
        socket1.setPid("1234");
        socket1.setProcess("java");
        socket1.setRecvQueue("0");
        socket1.setSendQueue("0");

        Socket socket2 = new Socket("udp", "ESTABLISHED", "192.168.1.100", "53", "8.8.8.8", "53");
        socket2.setPid("5678");
        socket2.setProcess("systemd-resolved");
        socket2.setRecvQueue("5");
        socket2.setSendQueue("3");

        testSockets.add(socket1);
        testSockets.add(socket2);

        tempDir = new File(System.getProperty("java.io.tmpdir"), "dumpports_test");
        tempDir.mkdirs();
    }

    @After
    public void tearDown() {
        // Clean up temporary files
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            tempDir.delete();
        }
    }

    @Test
    public void testExportToCSV() throws IOException {
        String filePath = new File(tempDir, "test_export.csv").getAbsolutePath();
        exportService.exportToCSV(testSockets, filePath);

        assertTrue("CSV file should exist", new File(filePath).exists());

        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        assertTrue("CSV should contain header", content.contains("Protocol,State,Local Address"));
        assertTrue("CSV should contain tcp data", content.contains("tcp"));
        assertTrue("CSV should contain udp data", content.contains("udp"));
        assertTrue("CSV should contain process names", content.contains("java"));
        assertTrue("CSV should contain systemd-resolved", content.contains("systemd-resolved"));
    }

    @Test
    public void testExportToJSON() throws IOException {
        String filePath = new File(tempDir, "test_export.json").getAbsolutePath();
        exportService.exportToJSON(testSockets, filePath);

        assertTrue("JSON file should exist", new File(filePath).exists());

        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        assertTrue("JSON should contain export date", content.contains("exportDate"));
        assertTrue("JSON should contain total sockets count", content.contains("totalSockets"));
        assertTrue("JSON should contain sockets array", content.contains("sockets"));
        assertTrue("JSON should contain tcp protocol", content.contains("tcp"));
        assertTrue("JSON should contain process data", content.contains("java"));
    }

    @Test
    public void testGenerateDefaultFilename() {
        String csvFilename = exportService.generateDefaultFilename("csv");
        String jsonFilename = exportService.generateDefaultFilename("json");

        assertTrue("CSV filename should start with dumpports_export_", csvFilename.startsWith("dumpports_export_"));
        assertTrue("CSV filename should end with .csv", csvFilename.endsWith(".csv"));
        assertTrue("JSON filename should start with dumpports_export_", jsonFilename.startsWith("dumpports_export_"));
        assertTrue("JSON filename should end with .json", jsonFilename.endsWith(".json"));
    }

    @Test
    public void testExportEmptyList() throws IOException {
        List<Socket> emptySockets = new ArrayList<>();
        String csvPath = new File(tempDir, "empty.csv").getAbsolutePath();

        exportService.exportToCSV(emptySockets, csvPath);

        assertTrue("CSV file should exist even for empty list", new File(csvPath).exists());

        String content = new String(Files.readAllBytes(Paths.get(csvPath)));
        assertTrue("CSV should contain only header for empty list", content.contains("Protocol,State"));
        assertEquals("CSV should have only one line for empty list", 1, content.split("\n").length);
    }

    @Test(expected = IOException.class)
    public void testExportToInvalidPath() throws IOException {
        String invalidPath = "/invalid/path/that/does/not/exist/file.csv";
        exportService.exportToCSV(testSockets, invalidPath);
    }
}

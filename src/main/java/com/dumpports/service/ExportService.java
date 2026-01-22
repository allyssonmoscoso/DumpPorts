package com.dumpports.service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Service class for exporting socket statistics to different formats.
 * Supports CSV and JSON export formats.
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Exports socket data to a CSV file.
     *
     * @param sockets List of Socket objects to export
     * @param filePath Path where the CSV file will be saved
     * @throws IOException If an error occurs while writing the file
     */
    public void exportToCSV(List<Socket> sockets, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            // Write header
            writer.write("Protocol,State,Local Address,Local Port,Remote Address,Remote Port,Process,PID,Recv Queue,Send Queue\n");

            // Write data rows
            for (Socket socket : sockets) {
                String line = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        escape(socket.getProtocol()),
                        escape(socket.getState()),
                        escape(socket.getLocalAddress()),
                        escape(socket.getLocalPort()),
                        escape(socket.getRemoteAddress()),
                        escape(socket.getRemotePort()),
                        escape(socket.getProcess()),
                        escape(socket.getPid()),
                        escape(socket.getRecvQueue()),
                        escape(socket.getSendQueue()));
                writer.write(line);
            }

            logger.info("Successfully exported {} sockets to CSV: {}", sockets.size(), filePath);
        } catch (IOException e) {
            logger.error("Error exporting sockets to CSV", e);
            throw e;
        }
    }

    /**
     * Exports socket data to a JSON file.
     *
     * @param sockets List of Socket objects to export
     * @param filePath Path where the JSON file will be saved
     * @throws IOException If an error occurs while writing the file
     */
    public void exportToJSON(List<Socket> sockets, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            
            ExportData exportData = new ExportData(sockets);
            String json = gson.toJson(exportData);
            
            writer.write(json);
            logger.info("Successfully exported {} sockets to JSON: {}", sockets.size(), filePath);
        } catch (IOException e) {
            logger.error("Error exporting sockets to JSON", e);
            throw e;
        }
    }

    /**
     * Generates a default export filename with timestamp.
     *
     * @param format Format type: "csv" or "json"
     * @return Filename with format and timestamp
     */
    public String generateDefaultFilename(String format) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        return String.format("dumpports_export_%s.%s", timestamp, format.toLowerCase());
    }

    /**
     * Escapes CSV special characters.
     *
     * @param value Value to escape
     * @return Escaped value safe for CSV
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Inner class to structure JSON export data with metadata.
     */
    public static class ExportData {
        public String exportDate;
        public int totalSockets;
        public List<Socket> sockets;

        public ExportData(List<Socket> sockets) {
            this.exportDate = LocalDateTime.now().toString();
            this.totalSockets = sockets.size();
            this.sockets = sockets;
        }
    }
}

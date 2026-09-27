package com.dumpports.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a long-lived privileged helper process launched through pkexec.
 *
 * <p>Authenticating with pkexec shows the system (PolicyKit) dialog. Starting a
 * single persistent helper means the user only authenticates once, even when the
 * UI auto-refresh keeps requesting data.</p>
 */
public class PrivilegedSocketHelper {

    private static final Logger logger = LoggerFactory.getLogger(PrivilegedSocketHelper.class);

    /** Marker emitted by the helper before the pid -> executable path section. */
    static final String EXE_MARKER = "\u001eEXE\u001e";
    /** Marker emitted by the helper after a complete DUMP response. */
    static final String END_MARKER = "\u001eEND\u001e";

    private static final String SCRIPT_RESOURCE = "/privileged/helper.sh";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 3;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Path scriptPath;

    /**
     * Starts the privileged helper via pkexec and prepares the communication
     * streams. This call blocks until pkexec has spawned the helper process, but
     * the actual PolicyKit authentication happens when {@link #dump()} is called.
     *
     * @throws IOException if the helper could not be started
     */
    public synchronized void start() throws IOException {
        if (isAlive()) {
            return;
        }

        scriptPath = extractScript();
        ProcessBuilder processBuilder = new ProcessBuilder("pkexec", "/bin/sh", scriptPath.toString());
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        process = processBuilder.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        logger.info("Privileged helper started (pkexec), awaiting authentication");
    }

    /**
     * Requests a fresh dump of socket statistics from the privileged helper.
     *
     * @return the raw ss lines together with the resolved executable paths
     * @throws IOException if the helper is not running or terminated unexpectedly
     */
    public synchronized SocketData dump() throws IOException {
        if (!isAlive() || writer == null || reader == null) {
            throw new IOException("Privileged helper is not running");
        }

        writer.write("DUMP\n");
        writer.flush();
        return parseResponse(reader);
    }

    /**
     * Parses a single DUMP response from the helper.
     *
     * <p>Package-private and static so it can be unit tested without spawning a
     * privileged process.</p>
     *
     * @param reader source to read the framed response from
     * @return the parsed socket data
     * @throws IOException if the stream ends before the END marker is found
     */
    static SocketData parseResponse(BufferedReader reader) throws IOException {
        List<String> ssLines = new ArrayList<>();
        Map<String, String> exeByPid = new LinkedHashMap<>();
        boolean inExeSection = false;

        String line;
        while ((line = reader.readLine()) != null) {
            if (END_MARKER.equals(line)) {
                return new SocketData(ssLines, exeByPid);
            }
            if (EXE_MARKER.equals(line)) {
                inExeSection = true;
                continue;
            }
            if (inExeSection) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    exeByPid.put(line.substring(0, tab), line.substring(tab + 1));
                }
            } else if (!line.isBlank()) {
                ssLines.add(line);
            }
        }

        throw new IOException("Privileged helper terminated unexpectedly");
    }

    /**
     * @return true if the helper process is still running
     */
    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Terminates the helper process and removes the temporary script.
     */
    public synchronized void close() {
        if (process != null) {
            try {
                if (writer != null && process.isAlive()) {
                    writer.write("QUIT\n");
                    writer.flush();
                }
            } catch (IOException ignored) {
                // Process already gone; nothing else to do.
            }
            try {
                if (!process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        writer = null;
        reader = null;
        deleteScript();
        logger.info("Privileged helper stopped");
    }

    private Path extractScript() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Helper script resource not found: " + SCRIPT_RESOURCE);
            }
            Path dir = Files.createTempDirectory("dumpports-privileged-");
            Path target = dir.resolve("helper.sh");
            Files.copy(in, target);
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException e) {
                logger.debug("POSIX permissions not supported on this platform");
            }
            target.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            return target;
        }
    }

    private void deleteScript() {
        if (scriptPath != null) {
            try {
                Files.deleteIfExists(scriptPath);
                Files.deleteIfExists(scriptPath.getParent());
            } catch (IOException e) {
                logger.debug("Could not remove temporary helper script", e);
            }
            scriptPath = null;
        }
    }

    /**
     * Raw socket data returned by the privileged helper.
     *
     * @param ssLines  lines produced by {@code ss -tunapH}
     * @param exeByPid map of PID to resolved executable path
     */
    public record SocketData(List<String> ssLines, Map<String, String> exeByPid) {
    }
}

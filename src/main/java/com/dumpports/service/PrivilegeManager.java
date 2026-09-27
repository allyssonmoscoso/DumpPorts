package com.dumpports.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks and manages the privilege level of the application.
 *
 * <p>A running JVM cannot elevate its own privileges. "Root mode" therefore
 * means that socket queries are delegated to a privileged helper process
 * launched through pkexec. The helper authenticates once and stays alive, so
 * auto-refresh does not keep prompting the user.</p>
 */
public class PrivilegeManager {

    /** Privilege levels the application can operate in. */
    public enum PrivilegeMode {
        USER,
        ROOT
    }

    private static final Logger logger = LoggerFactory.getLogger(PrivilegeManager.class);

    private PrivilegeMode mode;
    private PrivilegedSocketHelper helper;
    private String lastError;
    private boolean shutdownHookRegistered;

    public PrivilegeManager() {
        this.mode = isRunningAsRoot() ? PrivilegeMode.ROOT : PrivilegeMode.USER;
        if (this.mode == PrivilegeMode.ROOT) {
            logger.info("Application started with root privileges");
        }
    }

    /**
     * @return the current privilege mode
     */
    public synchronized PrivilegeMode getMode() {
        return mode;
    }

    /**
     * @return true when queries run with root privileges
     */
    public synchronized boolean isRootMode() {
        return mode == PrivilegeMode.ROOT;
    }

    /**
     * @return the privileged helper when in root mode, otherwise {@code null}
     */
    public synchronized PrivilegedSocketHelper getHelper() {
        return mode == PrivilegeMode.ROOT ? helper : null;
    }

    /**
     * @return a human-readable description of the last elevation failure,
     *         or {@code null} if the last attempt succeeded
     */
    public synchronized String getLastError() {
        return lastError;
    }

    /**
     * @return true if the pkexec binary is available on this system
     */
    public boolean isPkexecAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "pkexec");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            logger.debug("pkexec availability check failed", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempts to enable root mode by starting the privileged helper through
     * pkexec. Blocks until the helper responds to a first query, which happens
     * after the user completes (or cancels) the system authentication dialog.
     *
     * @return true if root mode is now active, false otherwise
     */
    public synchronized boolean requestRootMode() {
        if (mode == PrivilegeMode.ROOT) {
            return true;
        }
        if (!isPkexecAvailable()) {
            lastError = "PolicyKit (pkexec) is not available on this system.";
            logger.warn(lastError);
            return false;
        }

        closeHelper();
        PrivilegedSocketHelper candidate = new PrivilegedSocketHelper();
        try {
            candidate.start();
            // The first dump also performs the pkexec authentication handshake.
            candidate.dump();
            helper = candidate;
            mode = PrivilegeMode.ROOT;
            lastError = null;
            registerShutdownHook();
            logger.info("Root mode enabled");
            return true;
        } catch (Exception e) {
            candidate.close();
            mode = PrivilegeMode.USER;
            lastError = "Could not obtain root privileges: " + e.getMessage();
            logger.warn("Root mode request failed", e);
            return false;
        }
    }

    /**
     * Drops back to user mode and stops the privileged helper.
     */
    public synchronized void dropRootMode() {
        closeHelper();
        mode = isRunningAsRoot() ? PrivilegeMode.ROOT : PrivilegeMode.USER;
        logger.info("Privilege mode is now {}", mode);
    }

    /**
     * Called when the privileged helper unexpectedly becomes unavailable so the
     * application safely falls back to user mode.
     */
    public synchronized void reportHelperFailure() {
        if (mode == PrivilegeMode.ROOT) {
            logger.warn("Privileged helper is no longer available, falling back to user mode");
            closeHelper();
            mode = PrivilegeMode.USER;
            lastError = "Root privileges were lost. Operating in user mode.";
        }
    }

    /**
     * Releases any resources held by the manager. Safe to call multiple times.
     */
    public synchronized void close() {
        closeHelper();
    }

    private void closeHelper() {
        if (helper != null) {
            helper.close();
            helper = null;
        }
    }

    /**
     * Registers a JVM shutdown hook once so the privileged helper is always
     * terminated, even if the application exits unexpectedly.
     */
    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "dumpports-privilege-shutdown"));
        } catch (IllegalStateException e) {
            logger.debug("Could not register privilege shutdown hook", e);
        }
    }

    /**
     * Determines whether the current process is running as root.
     *
     * @return true if the effective user id is 0
     */
    private static boolean isRunningAsRoot() {
        try {
            ProcessBuilder pb = new ProcessBuilder("id", "-u");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                process.waitFor(3, TimeUnit.SECONDS);
                if (line != null) {
                    return "0".equals(line.trim());
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine user id, falling back to user.name", e);
        }
        return "root".equals(System.getProperty("user.name"));
    }
}

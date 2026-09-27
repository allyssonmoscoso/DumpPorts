package com.dumpports.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * Unit tests for PrivilegeManager.
 */
public class PrivilegeManagerTest {

    @Test
    public void testDefaultModeIsUserForNonRoot() {
        PrivilegeManager manager = new PrivilegeManager();
        try {
            if (!"root".equals(System.getProperty("user.name"))) {
                assertEquals(PrivilegeManager.PrivilegeMode.USER, manager.getMode());
            }
        } finally {
            manager.close();
        }
    }

    @Test
    public void testPkexecAvailabilityReturnsBoolean() {
        PrivilegeManager manager = new PrivilegeManager();
        try {
            // Should never throw, regardless of whether pkexec is installed.
            assertNotNull(Boolean.valueOf(manager.isPkexecAvailable()));
        } finally {
            manager.close();
        }
    }
}

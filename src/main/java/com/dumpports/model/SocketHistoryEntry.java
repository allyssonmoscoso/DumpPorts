package com.dumpports.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single connection history event for a socket.
 */
public class SocketHistoryEntry implements Serializable {
    public enum EventType {
        OPENED, CLOSED, STATE_CHANGED
    }

    private final long timestamp;
    private final EventType eventType;
    private final String state;
    private final String details;

    public SocketHistoryEntry(EventType eventType, String state, String details) {
        this.timestamp = Instant.now().toEpochMilli();
        this.eventType = eventType;
        this.state = state;
        this.details = details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getState() {
        return state;
    }

    public String getDetails() {
        return details;
    }
}

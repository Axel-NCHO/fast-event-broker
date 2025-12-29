package com.kylontech.experiment.events;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Events are the basic communication medium between actors. An event is scoped, meaning only actors
 * with a scope greater or equal can access it.
 */
public class Event {

    @NotNull
    private String type = "";
    @NotNull
    private String from = "";
    private byte[] payload = {};
    @NotNull
    private Long timestamp = 0L;

    @NotNull
    public String getType() {
        return type;
    }

    public void setType(@NotNull String type) {
        this.type = type;
    }

    @NotNull
    public String getFrom() {
        return from;
    }

    public void setFrom(@NotNull String from) {
        this.from = from;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @NotNull
    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(@NotNull Long timestamp) {
        this.timestamp = timestamp;
    }

    public String toString() {
        return "Event {type='"
                + this.type
                + "', from='"
                + this.from
                + "', payload='"
                + new String(this.payload, StandardCharsets.UTF_8)
                + "', timestamp='"
                + this.timestamp
                + "'}";
    }
}

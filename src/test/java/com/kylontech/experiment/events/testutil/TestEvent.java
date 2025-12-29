package com.kylontech.experiment.events.testutil;

import com.kylontech.experiment.events.Event;
import com.kylontech.experiment.events.EventRouter;
import com.kylontech.experiment.events.Scope;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

/**
 * Helper for registering events and event types.
 */
public class TestEvent {

    /**
     * Publish an event in a given router.
     */
    public static void publish(
            @NotNull String type,
            @NotNull String from,
            byte[] payload,
            long timestamp,
            @NotNull EventRouter router)
            throws IllegalAccessError, NoSuchElementException {
        Event e = new Event();
        e.setType(type);
        e.setFrom(from);
        e.setPayload(payload);
        e.setTimestamp(timestamp);
        router.publish(e);
    }

    /**
     * Register an event type in the given router. This is a no-op if the event type already exists.
     */
    public static void subscribe(
            @NotNull String type,
            @NotNull Scope scope,
            @NotNull TestSubscriber sub,
            @NotNull EventRouter router)
            throws IllegalStateException, IllegalArgumentException {
        try {
            router.registerEventType(type, scope, sub);
        } catch (IllegalStateException _) {
        }
        router.subscribe(type, sub);
    }
}

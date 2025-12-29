package com.kylontech.experiment.events;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A subscriber listens to a given number of event types in his scope's range.
 */
public abstract class Subscriber implements AutoCloseable {

    /***
     * This executor ensures events are processed one at a time, in the order they are received,
     * without blocking the event router. */
    @NotNull
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    /**
     * Return the scope of this subscriber.
     */
    @NotNull
    public abstract Scope scope();

    /**
     * Process an event received from a router.
     */
    protected abstract void processEvent(@NotNull Event e);

    /**
     * Send data to this subscriber. This is fast and does not block the caller.
     */
    public final void onEvent(@NotNull Event e) {
        this.exec.submit(() -> this.processEvent(e));
    }

    @Override
    public void close() throws TimeoutException, InterruptedException {
        this.exec.shutdown();
        if (!this.exec.awaitTermination(1, TimeUnit.MINUTES))
            throw new TimeoutException("subscriber's executor thread termination timed out");
    }
}

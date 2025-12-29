package com.kylontech.experiment.events;

import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global event type registry mapping event types to scopes.
 */
public class EventRegistry {

    private final @NotNull ConcurrentHashMap<String, Scope> scopes;

    public EventRegistry() {
        this.scopes = new ConcurrentHashMap<>();
    }

    /**
     * Register a new type of event with an associated scope.
     */
    public void register(@NotNull String type, @NotNull Scope s) throws IllegalStateException {
        if (this.scopes.putIfAbsent(type, s) != null)
            throw new IllegalStateException("Type '" + type + "' is already registered");
    }

    /**
     * Unregister a type of event.
     */
    public void unregister(@NotNull String type) {
        this.scopes.remove(type);
    }

    /**
     * Return the scope of a registered event type.
     */
    public Scope scopeOf(@NotNull String type) throws NoSuchElementException {
        Scope s = this.scopes.get(type);
        if (s == null) throw new NoSuchElementException("Type '" + type + "' is not registered");
        return s;
    }

    /**
     * Return whether an event type is registered.
     */
    public boolean isRegistered(@NotNull String type) {
        return this.scopes.containsKey(type);
    }

}

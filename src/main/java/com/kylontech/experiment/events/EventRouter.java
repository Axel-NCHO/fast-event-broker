package com.kylontech.experiment.events;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * A router is an event bus for subscribers to attach to and receive relevant events.
 */
public class EventRouter implements AutoCloseable {

    public static final int BUFFER_SIZE = 1024 * 16;

    /**
     * Thread pool for dispatching events to subscribers.
     */
    private final ExecutorService DISPATCH_POOL =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Scope of this router.
     */
    private final @NotNull Scope scope;

    /**
     * This maps each event type to its scope.
     */
    private final @NotNull EventRegistry registry;

    /**
     * This maps each event types to its subscribers.
     */
    private final @NotNull ConcurrentHashMap<@NotNull String, @NotNull SubscriberList> subscribers;

    private final @NotNull Disruptor<@NotNull Event> disruptor;
    private final @NotNull RingBuffer<@NotNull Event> ringBuffer;

    public EventRouter(@NotNull Scope scope) {
        this.scope = scope;
        this.registry = new EventRegistry();
        this.subscribers = new ConcurrentHashMap<>();

        ThreadFactory dispatcherFactory = new EventDispatcherFactory();
        this.disruptor =
                new Disruptor<>(
                        Event::new,
                        BUFFER_SIZE,
                        dispatcherFactory,
                        ProducerType.MULTI,
                        new YieldingWaitStrategy());
        this.disruptor.handleEventsWith(this::dispatch);
        this.ringBuffer = this.disruptor.getRingBuffer();
        this.registerDefaultEventTypes();

        this.disruptor.start();
    }

    /**
     * Register a new event type.
     *
     * @param type  event type
     * @param scope event scope
     * @param sub   event creator, must have enough scope to create this event
     */
    public void registerEventType(@NotNull String type, @NotNull Scope scope, @NotNull Subscriber sub)
            throws IllegalStateException, IllegalAccessError {
        if (this.scope.compareTo(scope) < 0)
            throw new IllegalAccessError("Invalid scope for this router");
        if (sub.scope().compareTo(scope) < 0)
            throw new IllegalAccessError("Insufficient scope to register this event");
        this.registry.register(type, scope);
        this.subscribers.put(type, new SubscriberList());
    }

    /**
     * Unregister an event type. The event type is not unregistered if there is still subscribers.
     *
     * @param type event type
     * @param sub  event creator or subscriber, must have enough scope to remove this event
     */
    public void unregisterEventType(@NotNull String type, @NotNull Subscriber sub)
            throws NoSuchElementException, IllegalAccessError {
        if (sub.scope().compareTo(this.registry.scopeOf(type)) < 0)
            throw new IllegalAccessError("Insufficient scope to unregister this event");
        var holder = this.subscribers.get(type);
        var list = holder.list;
        if (list.isEmpty()) {
            this.registry.unregister(type);
            this.subscribers.remove(type);
        }
    }

    /**
     * Subscribe to an event type.
     *
     * @param type event type
     * @param sub  event subscriber, must have enough scope to subscribe to this event
     */
    public void subscribe(@NotNull String type, @NotNull Subscriber sub)
            throws NoSuchElementException, IllegalAccessError {
        if (sub.scope().compareTo(this.registry.scopeOf(type)) < 0)
            throw new IllegalAccessError("Insufficient scope to subscribe to this event type");
        SubscriberList holder = this.subscribers.get(type);
        var oldList = holder.list;
        var newList = new ArrayList<>(oldList);
        newList.add(sub);
        holder.list = List.copyOf(newList);
    }

    /**
     * Unsubscribe to an event type. This is safe to use if the event type is not registered.
     *
     * @param type event type
     * @param sub  event subscriber
     */
    public void unsubscribe(@NotNull String type, @NotNull Subscriber sub)
            throws NoSuchElementException {
        if (sub.scope().compareTo(this.registry.scopeOf(type)) < 0)
            throw new IllegalAccessError("Insufficient scope to unsubscribe to this event type");
        SubscriberList holder = this.subscribers.get(type);
        var oldList = holder.list;
        var newList = new ArrayList<>(oldList);
        newList.remove(sub);
        holder.list = List.copyOf(newList);
    }

    /**
     * Publish an event in the event bus.
     */
    public void publish(@NotNull Event e) throws IllegalAccessError, NoSuchElementException {
        String type = e.getType();
        if (!this.registry.isRegistered(type))
            throw new NoSuchElementException("Even type '" + type + "' is not registered");
        long sequence = this.ringBuffer.next();
        try {
            Event bufferedEvent = ringBuffer.get(sequence);
            bufferedEvent.setType(e.getType());
            bufferedEvent.setFrom(e.getFrom());
            bufferedEvent.setPayload(e.getPayload());
            bufferedEvent.setTimestamp(e.getTimestamp());
        } finally {
            this.ringBuffer.publish(sequence);
        }
    }

    /**
     * Dispatch an event to all its subscribers.
     */
    private void dispatch(@NotNull Event e, long sequence, boolean endOfBatch) {
        SubscriberList holder = this.subscribers.get(e.getType());
        if (holder == null) return;
        var subs = holder.list;
        if (subs.isEmpty()) return;
        if (subs.size() == 1) subs.getFirst().onEvent(e);
        else {
            for (Subscriber sub : subs) DISPATCH_POOL.submit(() -> sub.onEvent(e));
        }
    }

    /**
     * Blocks until all published events have been dispatched and processed.
     */
    public boolean awaitEmptyTimedOut(long timeout, @NotNull TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < endTime) {
            long cursor = ringBuffer.getCursor();
            long lastSequence = disruptor.getRingBuffer().getMinimumGatingSequence();
            if (cursor == lastSequence) return false;
            LockSupport.parkNanos(1_000_000);
        }
        return true;
    }

    @Override
    public void close() throws TimeoutException, InterruptedException {
        try {
            this.disruptor.shutdown(1, TimeUnit.MINUTES);
        } catch (com.lmax.disruptor.TimeoutException _) {
            throw new TimeoutException("disruptor shutdown timed out");
        }
        DISPATCH_POOL.shutdown();
        if (!DISPATCH_POOL.awaitTermination(1, TimeUnit.MINUTES))
            throw new TimeoutException("dispatch pool termination timed out");
    }

    /**
     * Register default event types.
     */
    private void registerDefaultEventTypes() {
        this.registry.register("*", this.scope);
        for (@NotNull Scope s : Scope.values()) {
            if (this.scope.compareTo(s) >= 0) this.registry.register("*" + s, s);
        }
    }

    /**
     * Thin wrapper around a volatile list because java does not support volatile values for maps.
     */
    static class SubscriberList {
        volatile List<Subscriber> list = List.of();
    }

    /**
     * Custom thread factory for event dispatchers. An event dispatcher is a thread that sends
     * relevant events to a given subscriber. Event dispatchers have the maximum priority for
     * performance optimization.
     */
    static class EventDispatcherFactory implements ThreadFactory {

        private final @NotNull AtomicLong count = new AtomicLong(0);

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("EventDispatcher #" + count.getAndIncrement());
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    }
}

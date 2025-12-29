package com.kylontech.experiment.events.benchmark;

import com.kylontech.experiment.events.Event;
import com.kylontech.experiment.events.EventRouter;
import com.kylontech.experiment.events.Scope;
import com.kylontech.experiment.events.Subscriber;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark of mesh.core.EventRouter. Dispatching 1M events from several publishers to several
 * subscribers.
 */
public class EventRouterBenchmark {

    static void main() throws Exception {
        int numProducers = 4;
        int numConsumersPerType = 4;
        int numEventTypes = 5;
        int eventsPerProducer = 250_000;
        int epochs = 50;
        int nextEpoch = 1;
        long millis = 0;
        do {
            millis +=
                    benchmark(numProducers, numConsumersPerType, numEventTypes, eventsPerProducer, nextEpoch)
                            / epochs;
            nextEpoch++;
        } while (nextEpoch <= epochs);
        System.out.println();
        long totalEvents = totalDispatches(numProducers, numConsumersPerType, eventsPerProducer);
        System.out.println(
                "Avg: " + "Processed approx " + totalEvents + " events in " + millis + " ms");
    }

    /**
     * Measure time needed in milliseconds to dispatch events.
     */
    private static long benchmark(
            int numProducers,
            int numConsumersPerType,
            int numEventTypes,
            int eventsPerProducer,
            long epoch)
            throws InterruptedException, TimeoutException {
        try (EventRouter router = new EventRouter(Scope.SCOPE_PRIVATE);
             ExecutorService producerPool = Executors.newFixedThreadPool(numProducers)) {
            List<String> eventTypes = new ArrayList<>();
            for (int i = 0; i < numEventTypes; i++) eventTypes.add("EVENT_" + i);

            List<BenchSubscriber> subs = new ArrayList<>();
            for (String type : eventTypes) {
                for (int i = 0; i < numConsumersPerType; i++) {
                    BenchSubscriber s = new BenchSubscriber();
                    try {
                        router.registerEventType(type, Scope.SCOPE_PUBLIC, s);
                    } catch (IllegalStateException _) {
                    }
                    router.subscribe(type, s);
                    subs.add(s);
                }
            }

            long start = System.currentTimeMillis();

            for (int p = 0; p < numProducers; p++) {
                producerPool.submit(
                        () -> {
                            for (int i = 0; i < eventsPerProducer; i++) {
                                String type = eventTypes.get(i % numEventTypes);
                                Event e = new Event();
                                e.setType(type);
                                e.setFrom("producer");
                                e.setPayload(null);
                                e.setTimestamp(System.currentTimeMillis());
                                router.publish(e);
                            }
                        });
            }
            producerPool.shutdown();
            if (!producerPool.awaitTermination(1, TimeUnit.MINUTES)) {
                throw new TimeoutException("producer pool termination timed out");
            }
            if (router.awaitEmptyTimedOut(1, TimeUnit.MINUTES)) {
                throw new TimeoutException("wait for event router to be empty timed out");
            }
            subs.forEach(
                    (s) -> {
                        try {
                            s.close();
                        } catch (TimeoutException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });

            long end = System.currentTimeMillis();
            long totalEvents = totalDispatches(numProducers, numConsumersPerType, eventsPerProducer);
            long millis = end - start;
            System.out.println(
                    "Epoch #" + epoch + ": Processed approx " + totalEvents + " events in " + millis + " ms");
            return millis;
        }
    }

    private static long totalDispatches(
            int numProducers, int numConsumersPerType, int eventsPerProducer) {
        // Actually numProducers * eventsPerProducer * (numConsumersPerType / numEventTypes) *
        // numEventTypes
        // but numEventTypes disappears
        return (long) numProducers * eventsPerProducer * numConsumersPerType;
    }

    private static class BenchSubscriber extends Subscriber {
        public final AtomicLong received = new AtomicLong(0L);

        public @NotNull Scope scope() {
            return Scope.SCOPE_PUBLIC;
        }

        @Override
        protected void processEvent(@NotNull Event e) {
            received.getAndIncrement();
        }
    }
}

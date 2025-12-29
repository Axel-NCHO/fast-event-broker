package com.kylontech.experiment.events;

import com.kylontech.experiment.events.testutil.TestEvent;
import com.kylontech.experiment.events.testutil.TestSubscriber;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EventRouterTest {

    private EventRouter router;

    @BeforeEach
    void createRouter() {
        router = new EventRouter(Scope.SCOPE_PRIVATE);
    }

    @AfterEach
    void destroyRouter() throws InterruptedException, TimeoutException {
        boolean failForTimeout = awaitRouterEmptyTimedOut();
        router.close();
        if (failForTimeout) fail("wait for event router to be empty timed out");
    }

    void closeSubscriber(@NotNull TestSubscriber s) {
        try {
            s.close();
        } catch (TimeoutException _) {
            fail("sub's termination timed out");
        } catch (InterruptedException e) {
            fail("sub's termination was interrupted");
        }
    }

    boolean awaitRouterEmptyTimedOut() {
        return router.awaitEmptyTimedOut(1, TimeUnit.MINUTES);
    }

    /**
     * Test that a single subscriber receives events.
     */
    @Test
    void testSingleSubscriberReceivesEvents() {
        /* Set up */
        TestSubscriber s = TestSubscriber.createPublic();
        String eventType = "PING";
        TestEvent.subscribe(eventType, Scope.SCOPE_PUBLIC, s, router);

        /* Execute */
        TestEvent.publish(
                eventType, "", "test".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis(), router);
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(s);

        /* Verify */
        assertEquals(1, s.getReceived().size());
        assertEquals(eventType, s.getReceived().getFirst().getType());
    }

    /**
     * Test that a multiple subscribers receive events.
     */
    @Test
    void testMultipleSubscribersAllReceive() {
        /* Set up */
        var subs = IntStream.range(0, 10).mapToObj(_ -> TestSubscriber.createPublic()).toList();
        String eventType = "PING";
        subs.forEach((s) -> TestEvent.subscribe(eventType, Scope.SCOPE_PUBLIC, s, router));

        /* Execute */
        TestEvent.publish(
                eventType, "", "test".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis(), router);
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }

        /* Verify */
        subs.forEach(
                (s) -> {
                    closeSubscriber(s);
                    assertEquals(1, s.getReceived().size());
                    assertEquals(eventType, s.getReceived().getFirst().getType());
                });
    }

    /**
     * Test that a multiple subscribers can publish events.
     */
    @Test
    void testConcurrentPublishing() throws Exception {
        /* Set up */
        TestSubscriber s = TestSubscriber.createPublic();
        String eventType = "PING";
        TestEvent.subscribe(eventType, Scope.SCOPE_PUBLIC, s, router);

        /* Execute */
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 1000; i++) {
                int id = i;
                pool.submit(
                        () ->
                                TestEvent.publish(
                                        eventType,
                                        "",
                                        ("test " + id).getBytes(StandardCharsets.UTF_8),
                                        System.currentTimeMillis(),
                                        router));
            }
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES))
                fail("wait for process pool termination timed out");
        }
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(s);

        /* Verify */
        assertEquals(1000, s.getReceived().size());
    }

    /**
     * Test that an exception is raised when creating an event type with a scope that is not
     * compatible with the router's scope.
     */
    @Test
    void testExceptionOnCreateEventTypeWithInvalidRouterScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber s = TestSubscriber.createRoot();

        /* Execute */
        IllegalAccessError ex =
                assertThrows(
                        IllegalAccessError.class,
                        () -> router.registerEventType(eventType, Scope.SCOPE_ROOT, s));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(s);

        /* Verify */
        assertTrue(ex.getMessage().contains("Invalid scope"));
    }

    /**
     * Test that an exception is raised when a subscriber does not have enough scope to create an
     * event.
     */
    @Test
    void testExceptionOnCreateEventTypeWithInsufficientSubscriberScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber s = TestSubscriber.createPublic();

        /* Execute */
        IllegalAccessError ex =
                assertThrows(
                        IllegalAccessError.class,
                        () -> router.registerEventType(eventType, Scope.SCOPE_PRIVATE, s));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(s);

        /* Verify */
        assertTrue(ex.getMessage().contains("Insufficient scope"));
    }

    /**
     * Test that an exception is raised when a subscriber does not have enough scope to delete an
     * event.
     */
    @Test
    void testExceptionOnDeleteEventTypeWithInsufficientSubscriberScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPub = TestSubscriber.createPublic();
        TestSubscriber sPrivate = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivate);

        /* Execute */
        IllegalAccessError ex =
                assertThrows(IllegalAccessError.class, () -> router.unregisterEventType(eventType, sPub));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivate);
        closeSubscriber(sPub);

        /* Verify */
        assertTrue(ex.getMessage().contains("Insufficient scope"));
    }

    /**
     * Test that a subscriber with the same scope can delete an event.
     */
    @Test
    void testSuccessOnDeleteEventTypeWithExactSubscriberScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPrivateDel = TestSubscriber.createPrivate();
        TestSubscriber sPrivateCreate = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivateCreate);

        /* Execute & Verify */
        assertDoesNotThrow(() -> router.unregisterEventType(eventType, sPrivateDel));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivateCreate);
        closeSubscriber(sPrivateDel);
    }

    /**
     * Test that a subscriber with a broader scope can delete an event.
     */
    @Test
    void testSuccessOnDeleteEventTypeWithSuperiorSubscriberScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sRootDel = TestSubscriber.createRoot();
        TestSubscriber sPrivateCreate = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivateCreate);

        /* Execute & Verify */
        assertDoesNotThrow(() -> router.unregisterEventType(eventType, sRootDel));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivateCreate);
        closeSubscriber(sRootDel);
    }

    /**
     * Test that event types get automatically unregistered if there is no subscriber.
     */
    @Test
    void testEventTypeRemovedIfThereIsNoSubscriber() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPrivate1 = TestSubscriber.createPrivate();
        TestSubscriber sPrivate2 = TestSubscriber.createPrivate();
        TestEvent.subscribe(eventType, Scope.SCOPE_PRIVATE, sPrivate1, router);
        TestEvent.subscribe(eventType, Scope.SCOPE_PRIVATE, sPrivate2, router);

        /* Execute & Verify */
        router.unregisterEventType(eventType, sPrivate1);
        // This means the event type is still registered. If it wasn't, NoSuchElementException would be
        // raised.
        assertDoesNotThrow(() -> TestEvent.publish(eventType, "", null, System.currentTimeMillis(), router));

        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivate1);
        closeSubscriber(sPrivate2);
    }

    /**
     * Test that an exception is raised when trying to subscribe to an event with insufficient scope.
     */
    @Test
    void testExceptionOnSubscribeToEventTypeWithInsufficientScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPublic = TestSubscriber.createPublic();
        TestSubscriber sPrivate = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivate);

        /* Execute */
        IllegalAccessError ex =
                assertThrows(IllegalAccessError.class, () -> router.subscribe(eventType, sPublic));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPublic);
        closeSubscriber(sPrivate);

        /* Verify */
        assertTrue(ex.getMessage().contains("Insufficient scope"));
    }

    /**
     * Test that an exception is raised when trying to unsubscribe to an event with insufficient
     * scope.
     */
    @Test
    void testExceptionOnUnSubscribeToEventTypeWithInsufficientScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPublic = TestSubscriber.createPublic();
        TestSubscriber sPrivate = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivate);

        /* Execute */
        IllegalAccessError ex =
                assertThrows(IllegalAccessError.class, () -> router.unsubscribe(eventType, sPublic));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPublic);
        closeSubscriber(sPrivate);

        /* Verify */
        assertTrue(ex.getMessage().contains("Insufficient scope"));
    }

    /**
     * Test that a subscriber with enough scope can unsubscribe to an event.
     */
    @Test
    void testSuccessOnUnSubscribeToEventTypeWithSufficientScope() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPrivate1 = TestSubscriber.createPrivate();
        TestSubscriber sPrivate2 = TestSubscriber.createPrivate();
        router.registerEventType(eventType, Scope.SCOPE_PRIVATE, sPrivate2);

        /* Execute & Verify */
        assertDoesNotThrow(() -> router.unsubscribe(eventType, sPrivate1));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivate1);
        closeSubscriber(sPrivate2);
    }

    /**
     * Test that an exception is raised when trying to unregister to an event type that is not
     * registered.
     */
    @Test
    void testExceptionOnUnsubscribeToEventTypeNotRegistered() {
        /* Set up */
        String eventType = "EVENT";
        TestSubscriber sPrivate = TestSubscriber.createPrivate();

        /* Execute */
        NoSuchElementException ex =
                assertThrows(NoSuchElementException.class, () -> router.unsubscribe(eventType, sPrivate));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }
        closeSubscriber(sPrivate);

        /* Verify */
        assertTrue(ex.getMessage().contains("'" + eventType + "'" + " is not registered"));
    }

    /**
     * Test that an exception is raised when trying to publish an event that is not registered.
     */
    @Test
    void testExceptionOnPublishEventNotRegistered() {
        /* Set up */
        String eventType = "EVENT";

        /* Execute */
        NoSuchElementException ex =
                assertThrows(
                        NoSuchElementException.class,
                        () -> TestEvent.publish(eventType, "", null, System.currentTimeMillis(), router));
        if (awaitRouterEmptyTimedOut()) {
            fail("wait for event router to be empty timed out");
        }

        /* Verify */
        assertTrue(ex.getMessage().contains("'" + eventType + "'" + " is not registered"));
    }
}

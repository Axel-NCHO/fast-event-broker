package com.kylontech.experiment.events.testutil;

import com.kylontech.experiment.events.Event;
import com.kylontech.experiment.events.Scope;
import com.kylontech.experiment.events.Subscriber;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Subscriber implementation for tests.
 */
public class TestSubscriber extends Subscriber {

    final List<Event> received = Collections.synchronizedList(new ArrayList<>());

    /**
     * Subscriber with scope SCOPE_PUBLIC.
     */
    @NotNull
    public static TestSubscriber createPublic() {
        return new TestSubscriber() {
            @Override
            public @NotNull Scope scope() {
                return Scope.SCOPE_PUBLIC;
            }
        };
    }

    /**
     * Subscriber with scope SCOPE_FEDERATED.
     */
    @NotNull
    public static TestSubscriber createFederated() {
        return new TestSubscriber() {
            @Override
            public @NotNull Scope scope() {
                return Scope.SCOPE_FEDERATED;
            }
        };
    }

    /**
     * Subscriber with scope SCOPE_PRIVATE.
     */
    @NotNull
    public static TestSubscriber createPrivate() {
        return new TestSubscriber() {
            @Override
            public @NotNull Scope scope() {
                return Scope.SCOPE_PRIVATE;
            }
        };
    }

    /**
     * Subscriber with scope SCOPE_ROOT.
     */
    @NotNull
    public static TestSubscriber createRoot() {
        return new TestSubscriber() {
            @Override
            public @NotNull Scope scope() {
                return Scope.SCOPE_ROOT;
            }
        };
    }

    /**
     * Get received events.
     */
    public List<Event> getReceived() {
        return received;
    }

    @Override
    public @NotNull Scope scope() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    protected void processEvent(@NotNull Event e) {
        received.add(e);
    }
}

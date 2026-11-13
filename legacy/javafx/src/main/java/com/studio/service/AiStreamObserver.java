package com.studio.service;

/**
 * Receives text pieces as a provider streams them, so the chat UI can show a reply building up
 * instead of a spinner that never changes for the whole call.
 *
 * <p>Callbacks run on the worker thread that owns the HTTP stream, never on the JavaFX thread.
 * Implementations must therefore be thread-safe and must not touch UI nodes directly.</p>
 */
@FunctionalInterface
public interface AiStreamObserver {

    /** One non-empty piece of the answer, in arrival order. */
    void onDelta(String text);

    /**
     * The observer used when nothing listens. It is a singleton on purpose: call sites compare by
     * identity to keep the legacy (non-streaming) call overloads on the hot path, which is also what
     * lets subclasses and test doubles still intercept the classic signatures.
     */
    AiStreamObserver NONE = deltas -> {
        // Intentionally empty: a call site without a listener must not pay for streaming bookkeeping.
    };

    /** Observer used when nothing listens: no allocation, no branches on the hot path. */
    static AiStreamObserver none() {
        return NONE;
    }

    /** True when {@code observer} is the shared no-op instance or absent. */
    static boolean isNone(AiStreamObserver observer) {
        return observer == null || observer == NONE;
    }
}

package com.studio.service;

import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates hard-cancel of an in-process {@link TestRunnerService} run.
 * Not used for recording / other BusyButtons jobs.
 */
public final class RunCancelService {

    public static final String CANCEL_REASON = "Đã dừng bởi người dùng";

    private static final AtomicBoolean RUN_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean CANCEL_REQUESTED = new AtomicBoolean(false);
    private static final AtomicReference<SharedBrowserSession> ACTIVE_SESSION = new AtomicReference<>();
    private static final AtomicReference<Thread> WORKER = new AtomicReference<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private RunCancelService() {}

    public static void beginRun() {
        CANCEL_REQUESTED.set(false);
        ACTIVE_SESSION.set(null);
        WORKER.set(Thread.currentThread());
        RUN_ACTIVE.set(true);
        notifyListeners();
    }

    public static void endRun() {
        RUN_ACTIVE.set(false);
        CANCEL_REQUESTED.set(false);
        ACTIVE_SESSION.set(null);
        WORKER.set(null);
        notifyListeners();
    }

    public static boolean isRunActive() {
        return RUN_ACTIVE.get();
    }

    public static boolean isCancelRequested() {
        return CANCEL_REQUESTED.get();
    }

    public static void attachSession(SharedBrowserSession session) {
        ACTIVE_SESSION.set(session);
    }

    public static void clearSession(SharedBrowserSession session) {
        ACTIVE_SESSION.compareAndSet(session, null);
    }

    /** Request hard cancel: set flag, close Playwright session, interrupt worker. */
    public static void requestCancel() {
        if (!RUN_ACTIVE.get()) {
            return;
        }
        CANCEL_REQUESTED.set(true);
        SharedBrowserSession session = ACTIVE_SESSION.get();
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
                // best-effort unblock
            }
        }
        Thread worker = WORKER.get();
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
        }
        notifyListeners();
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** Test-only reset between unit tests. */
    static void resetForTests() {
        RUN_ACTIVE.set(false);
        CANCEL_REQUESTED.set(false);
        ACTIVE_SESSION.set(null);
        WORKER.set(null);
        LISTENERS.clear();
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                if (Platform.isFxApplicationThread()) {
                    listener.run();
                } else {
                    Platform.runLater(listener);
                }
            } catch (IllegalStateException toolkitNotReady) {
                listener.run();
            } catch (Exception ignored) {
                // listener must not break cancel path
            }
        }
    }
}

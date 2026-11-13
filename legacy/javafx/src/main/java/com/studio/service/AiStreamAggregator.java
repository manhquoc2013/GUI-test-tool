package com.studio.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects streamed model text so the chat bubble can show a live tail while one or more calls are
 * still running.
 *
 * <p>Two kinds of source exist. A <em>text</em> source keeps the tail of its output for the preview;
 * a <em>counted</em> source (a JSON contract, where raw deltas would be unreadable) only advances the
 * received-character counter. The aggregator is written for concurrent sub-agents: every method is
 * thread-safe, and each buffer is trimmed in place so a long answer cannot grow without bound.</p>
 */
public final class AiStreamAggregator {

    /** How much of the newest text one source keeps for the preview. */
    public static final int DEFAULT_MAX_BUFFER_CHARS = 2_000;
    /** Name used when a caller does not label its stream. */
    public static final String DEFAULT_SOURCE = "model";

    /** What the UI needs to paint one frame: how much arrived and what the newest text looks like. */
    public record Snapshot(int textSources, String sourceName, String preview, long receivedChars) {
        public boolean hasContent() {
            return receivedChars > 0;
        }
    }

    private final int maxBufferChars;
    private final Map<String, StringBuilder> textBuffers = new ConcurrentHashMap<>();
    private final AtomicLong receivedChars = new AtomicLong();

    public AiStreamAggregator() {
        this(DEFAULT_MAX_BUFFER_CHARS);
    }

    public AiStreamAggregator(int maxBufferChars) {
        this.maxBufferChars = Math.max(1, maxBufferChars);
    }

    /** Drops everything so a new turn starts from an empty preview. */
    public void reset() {
        textBuffers.clear();
        receivedChars.set(0);
    }

    /** Appends viewable text from one named source, keeping only its newest {@code maxBufferChars}. */
    public void append(String source, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        receivedChars.addAndGet(delta.length());
        StringBuilder buffer = textBuffers.computeIfAbsent(normalize(source), key -> new StringBuilder());
        synchronized (buffer) {
            buffer.append(delta);
            int overflow = buffer.length() - maxBufferChars;
            if (overflow > 0) {
                buffer.delete(0, overflow);
            }
        }
    }

    /** Records progress from a source whose raw text must not be shown, such as a JSON contract. */
    public void count(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        receivedChars.addAndGet(delta.length());
    }

    /**
     * Drops one finished source, so a preview that was hidden because several agents wrote at once can
     * show the tail again once only one of them is still running. The character total is kept.
     */
    public void completeSource(String source) {
        textBuffers.remove(normalize(source));
    }

    public Snapshot snapshot() {
        int sources = textBuffers.size();
        if (sources != 1) {
            // Several models writing at once would interleave two unrelated answers; the UI shows the
            // counter instead of a corrupted tail.
            return new Snapshot(sources, "", "", receivedChars.get());
        }
        Map.Entry<String, StringBuilder> only = textBuffers.entrySet().iterator().next();
        StringBuilder buffer = only.getValue();
        synchronized (buffer) {
            return new Snapshot(1, only.getKey(), buffer.toString(), receivedChars.get());
        }
    }

    private static String normalize(String source) {
        return source == null || source.isBlank() ? DEFAULT_SOURCE : source.trim();
    }
}

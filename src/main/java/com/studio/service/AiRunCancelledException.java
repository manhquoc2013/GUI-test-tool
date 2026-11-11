package com.studio.service;

/**
 * Signals that the user stopped the AI turn that is currently running.
 *
 * <p>It is thrown by the streaming callback as soon as the run's cancel flag is set, so a provider
 * read loop unwinds at the next token instead of finishing an answer nobody is waiting for. It is
 * deliberately unchecked: the transport seams only declare checked {@code IOException}s, and a
 * cancellation must never be turned into a "the provider failed" retry.</p>
 */
public final class AiRunCancelledException extends RuntimeException {

    /** Stable message, so an aggregated log line names the user action instead of a null cause. */
    public static final String MESSAGE = "Người dùng đã dừng lượt sinh phản hồi";

    public AiRunCancelledException() {
        super(MESSAGE);
    }
}

package com.yshs.questmcp.core.tool;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Runs reads of game state on the game thread and waits for them with a timeout. Tools call sources only through
 * {@link #read}, so that game objects are never touched from the HTTP threads.
 */
public final class GameThread {
    private final Executor executor;
    private final Duration timeout;

    /**
     * @param executor runs tasks on the game thread
     * @param timeout  how long a tool waits for the game before giving up
     */
    public GameThread(Executor executor, Duration timeout) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Runs {@code read} on the game thread and returns its result.
     *
     * @throws ToolException if the game does not run it within the timeout
     */
    public <T> T read(Supplier<T> read) throws ToolException {
        CompletableFuture<T> result;
        try {
            result = CompletableFuture.supplyAsync(read, executor);
        } catch (RejectedExecutionException e) {
            throw new ToolException("The game is not accepting requests right now (it may be shutting down).");
        }
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Cancelling keeps the read from running later if the game thread is merely busy.
            result.cancel(false);
            throw new ToolException("The game did not respond within " + timeout.toMillis()
                    + " ms. It may be busy (e.g. loading a world); try again in a moment.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Interrupted while waiting for the game.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}

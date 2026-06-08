package com.hlag.sourceviewer.domain.port.incoming;

import java.time.Instant;

/**
 * Use case for executing queued scan jobs.
 *
 * <p>Implemented by the application service in {@code application.scan}. Called by the
 * infrastructure-level dispatcher which manages the worker thread pool.</p>
 */
public interface ExecuteScanJobUseCase {

    /**
     * Atomically picks the next queued scan job, marks it as running, executes it,
     * and marks it as done or failed.
     *
     * @return {@code true} if a job was found and processed;
     *         {@code false} if the queue was empty
     */
    boolean tryExecuteNextJob();

    /**
     * Cleans up and marks as FAILED any RUNNING scan jobs whose last heartbeat occurred
     * before {@code staleBefore}. Safe to call from multiple instances simultaneously.
     *
     * @return the number of stale jobs recovered
     */
    int recoverStaleJobs(Instant staleBefore);
}

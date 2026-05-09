package com.hlag.sourceviewer.domain.port.incoming;

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
}

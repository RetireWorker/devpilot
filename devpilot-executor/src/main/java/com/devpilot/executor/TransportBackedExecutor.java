package com.devpilot.executor;

import com.devpilot.executor.client.ExecutorTransport;

/**
 * Common transport wiring. Concrete executors implement the interface(s) they
 * expose; this base only avoids duplicating endpoint plumbing.
 */
public abstract class TransportBackedExecutor {

    private final ExecutorTransport transport;
    private final String executorId;

    protected TransportBackedExecutor(ExecutorTransport transport, String executorId) {
        this.transport = transport;
        this.executorId = executorId;
    }

    protected ExecutorTransport transport() {
        return transport;
    }

    protected String executorId() {
        return executorId;
    }
}

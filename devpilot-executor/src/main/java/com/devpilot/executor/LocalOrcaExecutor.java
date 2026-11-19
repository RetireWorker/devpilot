package com.devpilot.executor;

import com.devpilot.common.domain.ExecutorMode;
import com.devpilot.executor.client.ExecutorTransport;

public class LocalOrcaExecutor extends OrcaExecutor {

    public LocalOrcaExecutor(ExecutorTransport transport) {
        super(transport, "local-orca", ExecutorMode.LOCAL);
    }
}

package com.devpilot.executor;

import com.devpilot.common.domain.ExecutorMode;
import com.devpilot.executor.client.ExecutorTransport;

public class RemoteOrcaExecutor extends OrcaExecutor {

    public RemoteOrcaExecutor(ExecutorTransport transport) {
        super(transport, "remote-orca", ExecutorMode.REMOTE);
    }
}

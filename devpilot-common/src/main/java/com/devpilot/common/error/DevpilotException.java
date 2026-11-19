package com.devpilot.common.error;

public class DevpilotException extends RuntimeException {
    public DevpilotException(String message) {
        super(message);
    }

    public DevpilotException(String message, Throwable cause) {
        super(message, cause);
    }
}

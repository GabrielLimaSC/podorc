package com.podorc.runtime.config;

/**
 * The agent definition could not be loaded: file missing, invalid YAML, or a required field absent
 * or of the wrong type. Thrown while a bean is being created, so it fails application startup with a
 * message that names the source and the offending field.
 */
public class AgentConfigException extends RuntimeException {

    public AgentConfigException(String message) {
        super(message);
    }

    public AgentConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

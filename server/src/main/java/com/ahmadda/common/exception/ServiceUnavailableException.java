package com.ahmadda.common.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(final String message) {
        super(message);
    }
}

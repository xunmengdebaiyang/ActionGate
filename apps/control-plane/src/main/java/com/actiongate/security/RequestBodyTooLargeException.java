package com.actiongate.security;

public final class RequestBodyTooLargeException extends RuntimeException {
    public RequestBodyTooLargeException() {
        super("Request body exceeds the configured limit");
    }
}

package com.mcode.gateway.exception;

public class MVCException extends RuntimeException {
    public MVCException() {
    }

    public MVCException(String message) {
        super(message);
    }
}

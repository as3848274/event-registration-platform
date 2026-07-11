package com.eventreg.exception;

public class EventFullException extends RuntimeException {
    public EventFullException(String message) {
        super(message);
    }
}

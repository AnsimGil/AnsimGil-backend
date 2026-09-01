package com.ansimgil.ansimgil_backend.flood;

public class FloodMapException extends RuntimeException {
    public enum Type {
        CONFIGURATION,
        REQUEST,
        UPSTREAM
    }

    private final Type type;

    public FloodMapException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public FloodMapException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}

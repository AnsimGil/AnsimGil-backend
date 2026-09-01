package com.ansimgil.ansimgil_backend.disaster;

public class DisasterException extends RuntimeException {
    public enum Type {
        UPSTREAM,
        INTERNAL
    }

    private final Type type;

    public DisasterException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public DisasterException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}

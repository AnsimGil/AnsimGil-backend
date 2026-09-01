package com.ansimgil.ansimgil_backend.place;

public class PlaceException extends RuntimeException {
    public enum Type {
        INVALID_REQUEST,
        CONFIGURATION,
        UPSTREAM,
        INTERNAL
    }

    private final Type type;

    public PlaceException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public PlaceException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}

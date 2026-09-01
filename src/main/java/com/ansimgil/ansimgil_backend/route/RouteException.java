package com.ansimgil.ansimgil_backend.route;

public class RouteException extends RuntimeException {
    public enum Type {
        CONFIGURATION,
        UPSTREAM,
        INTERNAL
    }

    private final Type type;

    public RouteException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public RouteException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}

package com.ansimgil.ansimgil_backend.weather;

public class WeatherException extends RuntimeException {
    public enum Type {
        CONFIGURATION,
        INVALID_REQUEST,
        UPSTREAM,
        INTERNAL
    }

    private final Type type;

    public WeatherException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public WeatherException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}

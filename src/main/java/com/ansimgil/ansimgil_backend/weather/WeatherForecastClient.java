package com.ansimgil.ansimgil_backend.weather;

public interface WeatherForecastClient {
    String fetch(KmaGridPoint grid, String baseDate, String baseTime);
}

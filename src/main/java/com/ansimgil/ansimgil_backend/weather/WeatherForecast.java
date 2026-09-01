package com.ansimgil.ansimgil_backend.weather;

public record WeatherForecast(
        String forecastDate,
        String forecastTime,
        Integer precipitationProbability,
        String precipitationType,
        String precipitationAmount,
        Double temperature
) {
}

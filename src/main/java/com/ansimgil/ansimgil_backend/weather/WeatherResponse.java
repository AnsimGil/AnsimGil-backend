package com.ansimgil.ansimgil_backend.weather;

import com.ansimgil.ansimgil_backend.route.RouteRequest;

import java.util.List;

public record WeatherResponse(
        String source,
        boolean live,
        RouteRequest.Coordinate userLocation,
        KmaGridPoint grid,
        String baseDate,
        String baseTime,
        WeatherRiskLevel riskLevel,
        String summary,
        String fallbackReason,
        List<WeatherForecast> forecasts
) {
}

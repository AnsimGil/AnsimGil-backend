package com.ansimgil.ansimgil_backend.weather;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping(value = "/short-term", produces = MediaType.APPLICATION_JSON_VALUE)
    public WeatherResponse getShortTerm(
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude
    ) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new WeatherException(
                    WeatherException.Type.INVALID_REQUEST,
                    "위도는 -90~90, 경도는 -180~180 범위여야 합니다."
            );
        }
        return weatherService.getShortTerm(new RouteRequest.Coordinate(latitude, longitude));
    }
}

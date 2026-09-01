package com.ansimgil.ansimgil_backend.weather;

import tools.jackson.core.JacksonException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class WeatherFixtureRepository {
    private static final String FIXTURE_PATH = "fixtures/weather-forecast.json";

    private final List<WeatherForecast> forecasts;

    public WeatherFixtureRepository(WeatherForecastNormalizer normalizer) {
        try (InputStream inputStream = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            String rawJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            this.forecasts = List.copyOf(normalizer.normalize(rawJson));
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("기상청 fixture를 읽지 못했습니다: " + FIXTURE_PATH, exception);
        }
    }

    public List<WeatherForecast> findAll() {
        return forecasts;
    }
}

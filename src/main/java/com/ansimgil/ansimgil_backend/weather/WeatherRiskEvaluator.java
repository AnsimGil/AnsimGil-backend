package com.ansimgil.ansimgil_backend.weather;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WeatherRiskEvaluator {
    public WeatherRiskLevel evaluate(List<WeatherForecast> forecasts) {
        int maximumProbability = forecasts.stream()
                .map(WeatherForecast::precipitationProbability)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        boolean concretePrecipitation = forecasts.stream().anyMatch(this::hasConcretePrecipitation);
        boolean precipitationSignal = forecasts.stream().anyMatch(this::hasPrecipitationSignal);

        if (maximumProbability >= 60 || concretePrecipitation) return WeatherRiskLevel.EXPECTED;
        if (maximumProbability >= 40 || precipitationSignal) return WeatherRiskLevel.POSSIBLE;
        return WeatherRiskLevel.NONE;
    }

    public String summarize(WeatherRiskLevel riskLevel) {
        return switch (riskLevel) {
            case EXPECTED -> "향후 예보에 강수 가능성이 높습니다.";
            case POSSIBLE -> "향후 예보에 강수 가능성이 있습니다.";
            case NONE -> "향후 예보에서 뚜렷한 강수 신호가 없습니다.";
        };
    }

    private boolean hasConcretePrecipitation(WeatherForecast forecast) {
        return hasPrecipitationType(forecast)
                && forecast.precipitationAmount() != null
                && !forecast.precipitationAmount().isBlank()
                && !forecast.precipitationAmount().contains("강수없음");
    }

    private boolean hasPrecipitationSignal(WeatherForecast forecast) {
        return hasPrecipitationType(forecast)
                || (forecast.precipitationProbability() != null && forecast.precipitationProbability() >= 40);
    }

    private boolean hasPrecipitationType(WeatherForecast forecast) {
        return forecast.precipitationType() != null
                && !forecast.precipitationType().equals("강수 없음");
    }
}

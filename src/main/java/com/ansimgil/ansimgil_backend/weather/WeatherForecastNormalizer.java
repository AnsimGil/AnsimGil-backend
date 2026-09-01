package com.ansimgil.ansimgil_backend.weather;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 기상청 응답의 시간·카테고리별 항목을 앱용 시간대 forecast로 묶습니다. */
@Component
public class WeatherForecastNormalizer {
    private final ObjectMapper objectMapper;

    public WeatherForecastNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WeatherForecast> normalize(String rawJson) {
        try {
            Object root = objectMapper.readValue(rawJson, Object.class);
            List<Map<?, ?>> rows = findItems(root);
            Map<String, MutableForecast> grouped = new LinkedHashMap<>();

            for (Map<?, ?> row : rows) {
                String date = firstText(row, "fcstDate", "FCST_DATE");
                String time = firstText(row, "fcstTime", "FCST_TIME");
                String category = firstText(row, "category", "CATEGORY").toUpperCase();
                String value = firstText(row, "fcstValue", "FCST_VALUE");

                if (date.isBlank() || time.isBlank()) continue;
                MutableForecast forecast = grouped.computeIfAbsent(
                        date + time,
                        ignored -> new MutableForecast(date, time)
                );
                apply(forecast, category, value);
            }

            List<WeatherForecast> forecasts = new ArrayList<>(grouped.values().stream()
                    .map(MutableForecast::toForecast)
                    .toList());
            forecasts.sort(Comparator.comparing(WeatherForecast::forecastDate)
                    .thenComparing(WeatherForecast::forecastTime));
            return forecasts;
        } catch (JacksonException | ClassCastException exception) {
            throw new WeatherException(
                    WeatherException.Type.INTERNAL,
                    "기상청 단기예보 응답을 표준 형식으로 변환하지 못했습니다.",
                    exception
            );
        }
    }

    private List<Map<?, ?>> findItems(Object value) {
        if (value instanceof List<?> list) {
            List<Map<?, ?>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) rows.add(map);
            }
            return rows;
        }
        if (!(value instanceof Map<?, ?> map)) return List.of();

        for (String key : List.of("item", "items")) {
            Object nested = valueFor(map, key);
            if (nested instanceof List<?> || nested instanceof Map<?, ?>) {
                List<Map<?, ?>> rows = findItems(nested);
                if (!rows.isEmpty()) return rows;
            }
        }

        for (Object nested : map.values()) {
            List<Map<?, ?>> rows = findItems(nested);
            if (!rows.isEmpty()) return rows;
        }
        return List.of();
    }

    private void apply(MutableForecast forecast, String category, String value) {
        switch (category) {
            case "POP" -> forecast.precipitationProbability = parseInteger(value);
            case "PTY" -> forecast.precipitationType = precipitationType(value);
            case "PCP" -> forecast.precipitationAmount = value;
            case "TMP" -> forecast.temperature = parseDouble(value);
            default -> {
                // MVP에서는 강수 관련 항목과 기온만 사용합니다.
            }
        }
    }

    private String precipitationType(String value) {
        return switch (value) {
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            case "5" -> "빗방울";
            case "6" -> "빗방울/눈날림";
            case "7" -> "눈날림";
            default -> "강수 없음";
        };
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = valueFor(map, key);
            if (value != null && !String.valueOf(value).trim().isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private Object valueFor(Map<?, ?> map, String expectedKey) {
        Object direct = map.get(expectedKey);
        if (direct != null) return direct;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equalsIgnoreCase(expectedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static class MutableForecast {
        private final String date;
        private final String time;
        private Integer precipitationProbability;
        private String precipitationType = "강수 없음";
        private String precipitationAmount = "강수없음";
        private Double temperature;

        private MutableForecast(String date, String time) {
            this.date = date;
            this.time = time;
        }

        private WeatherForecast toForecast() {
            return new WeatherForecast(
                    date,
                    time,
                    precipitationProbability,
                    precipitationType,
                    precipitationAmount,
                    temperature
            );
        }
    }
}

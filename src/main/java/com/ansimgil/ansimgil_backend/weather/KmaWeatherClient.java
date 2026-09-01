package com.ansimgil.ansimgil_backend.weather;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 기상청 단기예보 조회서비스의 HTTP adapter입니다. */
@Component
public class KmaWeatherClient implements WeatherForecastClient {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String serviceKey;
    private final int numOfRows;

    public KmaWeatherClient(
            @Value("${kma-weather.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl,
            @Value("${kma-weather.service-key:}") String serviceKey,
            @Value("${kma-weather.num-of-rows:1000}") int numOfRows
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.numOfRows = Math.max(1, numOfRows);
    }

    @Override
    public String fetch(KmaGridPoint grid, String baseDate, String baseTime) {
        if (serviceKey.isBlank()) {
            throw new WeatherException(
                    WeatherException.Type.CONFIGURATION,
                    "KMA_WEATHER_SERVICE_KEY 환경변수가 설정되지 않았습니다."
            );
        }

        String query = "serviceKey=" + encodeServiceKey(serviceKey)
                + "&pageNo=1"
                + "&numOfRows=" + numOfRows
                + "&dataType=JSON"
                + "&base_date=" + encode(baseDate)
                + "&base_time=" + encode(baseTime)
                + "&nx=" + grid.nx()
                + "&ny=" + grid.ny();

        URI uri;
        try {
            uri = URI.create(baseUrl + "/getVilageFcst?" + query);
        } catch (IllegalArgumentException exception) {
            throw new WeatherException(
                    WeatherException.Type.INTERNAL,
                    "기상청 단기예보 API 주소가 올바르지 않습니다.",
                    exception
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeatherException(
                        WeatherException.Type.UPSTREAM,
                        "기상청 단기예보 API 요청이 실패했습니다 (HTTP " + response.statusCode() + ")."
                );
            }

            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherException(
                    WeatherException.Type.UPSTREAM,
                    "기상청 단기예보 API 요청이 중단되었습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new WeatherException(
                    WeatherException.Type.UPSTREAM,
                    "기상청 단기예보 API 서버에 연결하지 못했습니다.",
                    exception
            );
        }
    }

    private String encodeServiceKey(String value) {
        // 공공데이터포털의 Encoding 키가 이미 %XX 형태로 복사된 경우 중복 인코딩을 피합니다.
        return value.contains("%") ? value : encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0"
                : value.trim();
        return normalized.replaceAll("/$", "");
    }
}

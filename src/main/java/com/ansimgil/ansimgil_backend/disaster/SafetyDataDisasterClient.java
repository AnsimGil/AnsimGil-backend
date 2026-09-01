package com.ansimgil.ansimgil_backend.disaster;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 행정안전부 안전데이터공유플랫폼의 긴급재난문자 API 호출 어댑터입니다.
 * 실제 호출은 DisasterService의 라이브 모드가 명시적으로 켜진 경우에만 발생합니다.
 */
@Component
public class SafetyDataDisasterClient implements DisasterMessageClient {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String serviceKey;
    private final String interfaceId;

    public SafetyDataDisasterClient(
            @Value("${disaster-data.base-url:https://www.safetydata.go.kr}") String baseUrl,
            @Value("${disaster-data.service-key:}") String serviceKey,
            @Value("${disaster-data.interface-id:DSSP-IF-00247}") String interfaceId
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.interfaceId = interfaceId == null ? "DSSP-IF-00247" : interfaceId.trim();
    }

    @Override
    public String fetch(int pageNo, int numOfRows, LocalDate startDate) {
        if (serviceKey.isBlank()) {
            throw new DisasterException(
                    DisasterException.Type.INTERNAL,
                    "DISASTER_DATA_SERVICE_KEY 환경변수가 설정되지 않았습니다."
            );
        }

        String query = "serviceKey=" + encode(serviceKey)
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows
                + "&crtDt=" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&returnType=json";
        URI uri;
        try {
            uri = URI.create(baseUrl + "/V2/api/" + interfaceId + "?" + query);
        } catch (IllegalArgumentException exception) {
            throw new DisasterException(
                    DisasterException.Type.INTERNAL,
                    "재난문자 API 주소가 올바르지 않습니다.",
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
                throw new DisasterException(
                        DisasterException.Type.UPSTREAM,
                        "긴급재난문자 API 요청이 실패했습니다 (HTTP " + response.statusCode() + ")."
                );
            }

            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DisasterException(
                    DisasterException.Type.UPSTREAM,
                    "긴급재난문자 API 요청이 중단되었습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new DisasterException(
                    DisasterException.Type.UPSTREAM,
                    "긴급재난문자 API 서버에 연결하지 못했습니다.",
                    exception
            );
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://www.safetydata.go.kr"
                : value.trim();
        return normalized.replaceAll("/$", "");
    }
}

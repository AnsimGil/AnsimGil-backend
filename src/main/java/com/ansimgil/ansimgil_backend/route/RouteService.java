package com.ansimgil.ansimgil_backend.route;

import com.ansimgil.ansimgil_backend.flood.FloodZoneService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouteService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final FloodZoneService floodZoneService;
    private final String apiKey;
    private final String baseUrl;

    public RouteService(
            ObjectMapper objectMapper,
            FloodZoneService floodZoneService,
            @Value("${ors.api-key:}") String apiKey,
            @Value("${ors.base-url:https://api.heigit.org/openrouteservice}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.floodZoneService = floodZoneService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    public String requestRoute(RouteRequest request, boolean avoidFloodZone) {
        if (apiKey.isBlank()) {
            throw new RouteException(
                    RouteException.Type.CONFIGURATION,
                    "ORS_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordinates", List.of(
                request.origin().asGeoJsonPosition(),
                request.destination().asGeoJsonPosition()
        ));

        if (avoidFloodZone) {
            payload.put("options", Map.of(
                    "avoid_polygons", floodZoneService.getAvoidGeometry()
            ));
        }

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/directions/driving-car/geojson"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/geo+json, application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RouteException(
                        RouteException.Type.UPSTREAM,
                        "ORS 경로 요청이 실패했습니다 (HTTP " + response.statusCode() + "). "
                                + response.body()
                );
            }

            return response.body();
        } catch (JacksonException exception) {
            throw new RouteException(
                    RouteException.Type.INTERNAL,
                    "ORS 요청 본문을 생성하지 못했습니다.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RouteException(
                    RouteException.Type.UPSTREAM,
                    "ORS 경로 요청이 중단되었습니다.",
                    exception
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new RouteException(
                    RouteException.Type.UPSTREAM,
                    "ORS 경로 서버에 연결하지 못했습니다.",
                    exception
            );
        }
    }
}

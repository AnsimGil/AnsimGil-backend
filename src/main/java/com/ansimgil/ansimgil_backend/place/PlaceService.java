package com.ansimgil.ansimgil_backend.place;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceService {
    private static final String SOURCE = "GOOGLE_PLACES";
    private static final String AUTOCOMPLETE_FIELD_MASK =
            "suggestions.placePrediction.placeId," +
                    "suggestions.placePrediction.text," +
                    "suggestions.placePrediction.structuredFormat," +
                    "suggestions.placePrediction.types";
    private static final String DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location,types";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResults;

    public PlaceService(
            ObjectMapper objectMapper,
            @Value("${places.base-url:https://places.googleapis.com/v1}") String baseUrl,
            @Value("${places.api-key:}") String apiKey,
            @Value("${places.max-results:5}") int maxResults
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxResults = Math.max(1, Math.min(maxResults, 10));
    }

    public PlaceSearchResponse autocomplete(String input) {
        String normalizedInput = requireInput(input, "검색어");
        ensureConfigured();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", normalizedInput);
        payload.put("languageCode", "ko");
        payload.put("regionCode", "KR");
        payload.put("includedRegionCodes", List.of("kr"));

        String responseBody = sendPost("/places:autocomplete", payload, AUTOCOMPLETE_FIELD_MASK);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<PlaceSuggestion> suggestions = new ArrayList<>();
            JsonNode rawSuggestions = root.path("suggestions");
            if (rawSuggestions.isArray()) {
                for (JsonNode suggestion : rawSuggestions) {
                    JsonNode prediction = suggestion.path("placePrediction");
                    if (prediction.isMissingNode() || prediction.isNull()) continue;

                    String placeId = textAt(prediction, "placeId");
                    String fullText = textAt(prediction.path("text"), "text");
                    String primaryText = textAt(prediction.path("structuredFormat").path("mainText"), "text");
                    String secondaryText = textAt(prediction.path("structuredFormat").path("secondaryText"), "text");
                    if (primaryText.isBlank()) primaryText = fullText;
                    if (fullText.isBlank()) fullText = primaryText;
                    if (placeId.isBlank() || primaryText.isBlank()) continue;

                    List<String> types = new ArrayList<>();
                    JsonNode rawTypes = prediction.path("types");
                    if (rawTypes.isArray()) {
                        rawTypes.forEach(type -> types.add(type.asText()));
                    }
                    suggestions.add(new PlaceSuggestion(
                            placeId,
                            primaryText,
                            secondaryText,
                            fullText,
                            types
                    ));
                    if (suggestions.size() >= maxResults) break;
                }
            }
            return new PlaceSearchResponse(SOURCE, true, suggestions);
        } catch (JacksonException exception) {
            throw new PlaceException(
                    PlaceException.Type.INTERNAL,
                    "Google 장소 검색 응답을 해석하지 못했습니다.",
                    exception
            );
        }
    }

    public PlaceDetailsResponse details(String placeId) {
        String normalizedPlaceId = requireInput(placeId, "장소 ID");
        ensureConfigured();

        String responseBody = sendGet(
                "/places/" + encodePathSegment(normalizedPlaceId),
                DETAILS_FIELD_MASK
        );
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String resolvedPlaceId = textAt(root, "id");
            String name = textAt(root.path("displayName"), "text");
            String address = textAt(root, "formattedAddress");
            JsonNode location = root.path("location");
            double latitude = location.path("latitude").asDouble(Double.NaN);
            double longitude = location.path("longitude").asDouble(Double.NaN);

            if (resolvedPlaceId.isBlank() || name.isBlank()
                    || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                throw new PlaceException(
                        PlaceException.Type.UPSTREAM,
                        "Google 장소 상세정보에 좌표가 없습니다."
                );
            }

            return new PlaceDetailsResponse(
                    SOURCE,
                    true,
                    resolvedPlaceId,
                    name,
                    address,
                    new RouteRequest.Coordinate(latitude, longitude)
            );
        } catch (JacksonException exception) {
            throw new PlaceException(
                    PlaceException.Type.INTERNAL,
                    "Google 장소 상세정보 응답을 해석하지 못했습니다.",
                    exception
            );
        }
    }

    private String sendPost(String path, Map<String, Object> payload, String fieldMask) {
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", fieldMask)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return send(request);
        } catch (JacksonException exception) {
            throw new PlaceException(
                    PlaceException.Type.INTERNAL,
                    "Google 장소 검색 요청을 생성하지 못했습니다.",
                    exception
            );
        }
    }

    private String sendGet(String path, String fieldMask) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", fieldMask)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new PlaceException(
                    PlaceException.Type.INTERNAL,
                    "Google 장소 상세정보 주소가 올바르지 않습니다.",
                    exception
            );
        }
        return send(request);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlaceException(
                        PlaceException.Type.UPSTREAM,
                        "Google 장소 API 요청이 실패했습니다 (HTTP " + response.statusCode() + ")."
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaceException(
                    PlaceException.Type.UPSTREAM,
                    "Google 장소 API 요청이 중단되었습니다.",
                    exception
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new PlaceException(
                    PlaceException.Type.UPSTREAM,
                    "Google 장소 API 서버에 연결하지 못했습니다.",
                    exception
            );
        }
    }

    private void ensureConfigured() {
        if (apiKey.isBlank()) {
            throw new PlaceException(
                    PlaceException.Type.CONFIGURATION,
                    "GOOGLE_PLACES_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }

    private String requireInput(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 2) {
            throw new PlaceException(
                    PlaceException.Type.INVALID_REQUEST,
                    label + "는 2자 이상 입력해야 합니다."
            );
        }
        return normalized;
    }

    private String textAt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://places.googleapis.com/v1"
                : value.trim();
        return normalized.replaceAll("/$", "");
    }
}

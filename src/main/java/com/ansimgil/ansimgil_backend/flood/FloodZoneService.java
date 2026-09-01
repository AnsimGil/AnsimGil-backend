package com.ansimgil.ansimgil_backend.flood;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FloodZoneService {
    private static final String FIXTURE_PATH = "fixtures/flood-zones.json";

    private final JsonNode demoGeoJson;
    private final JsonNode emptyGeoJson;
    private final String requestedMode;
    private final String wmsUrl;
    private final String wmsLayer;
    private final String serviceKey;
    private final String frequency;
    private final String administrativeAreaCode;
    private final String depthCode;
    private final String srs;
    private final HttpClient httpClient;

    public FloodZoneService(
            ObjectMapper objectMapper,
            @Value("${flood-map.mode:demo}") String configuredMode,
            @Value("${flood-map.wms-url:}") String configuredWmsUrl,
            @Value("${flood-map.layer:}") String configuredWmsLayer,
            @Value("${flood-map.service-key:}") String configuredServiceKey,
            @Value("${flood-map.frequency:100}") String configuredFrequency,
            @Value("${flood-map.administrative-area-code:11100}") String configuredAdministrativeAreaCode,
            @Value("${flood-map.depth-code:}") String configuredDepthCode,
            @Value("${flood-map.srs:EPSG:4326}") String configuredSrs
    ) {
        this.requestedMode = normalizeMode(configuredMode);
        this.wmsUrl = configuredWmsUrl == null ? "" : configuredWmsUrl.trim();
        this.wmsLayer = configuredWmsLayer == null ? "" : configuredWmsLayer.trim();
        this.serviceKey = configuredServiceKey == null ? "" : configuredServiceKey.trim();
        this.frequency = configuredFrequency == null ? "100" : configuredFrequency.trim();
        this.administrativeAreaCode = configuredAdministrativeAreaCode == null
                ? "11100"
                : configuredAdministrativeAreaCode.trim();
        this.depthCode = configuredDepthCode == null ? "" : configuredDepthCode.trim();
        this.srs = configuredSrs == null ? "EPSG:4326" : configuredSrs.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        try (InputStream inputStream = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            this.demoGeoJson = objectMapper.readTree(inputStream);
            this.emptyGeoJson = objectMapper.createObjectNode()
                    .put("type", "FeatureCollection")
                    .set("features", objectMapper.createArrayNode());
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("FloodZone fixture를 읽지 못했습니다: " + FIXTURE_PATH, exception);
        }
    }

    public FloodZoneResponse getFloodZones() {
        return getFloodZones(false);
    }

    public FloodZoneResponse getFloodZones(boolean forceDemo) {
        if (!forceDemo && "live".equals(requestedMode) && isLiveConfigured()) {
            return new FloodZoneResponse(
                    "FLOOD_MAP_WMS",
                    requestedMode,
                    true,
                    null,
                    emptyGeoJson
            );
        }

        if (!forceDemo && "live".equals(requestedMode)) {
            return new FloodZoneResponse(
                    "LOCAL_DEMO",
                    requestedMode,
                    false,
                    "LIVE_WMS_NOT_CONFIGURED",
                    demoGeoJson
            );
        }

        return new FloodZoneResponse(
                "LOCAL_DEMO",
                requestedMode,
                false,
                null,
                demoGeoJson
        );
    }

    public boolean isLiveConfigured() {
        return "live".equals(requestedMode)
                && !wmsUrl.isBlank()
                && !serviceKey.isBlank()
                && !frequency.isBlank()
                && !administrativeAreaCode.isBlank()
                && "EPSG:4326".equalsIgnoreCase(srs);
    }

    /**
     * Fetches one Google-map-compatible WMS tile. The caller supplies a tile
     * bounding box in EPSG:900913; the official provider is called in EPSG:4326
     * because that is the documented geographic coordinate option.
     */
    public byte[] fetchWmsTile(double minX, double maxX, double minY, double maxY, int width, int height) {
        if (!isLiveConfigured()) {
            throw new FloodMapException(
                    FloodMapException.Type.CONFIGURATION,
                    "홍수위험지도 LIVE 설정이 완전하지 않습니다."
            );
        }
        if (!isFinite(minX) || !isFinite(maxX) || !isFinite(minY) || !isFinite(maxY)
                || minX >= maxX || minY >= maxY) {
            throw new FloodMapException(
                    FloodMapException.Type.REQUEST,
                    "홍수위험지도 타일 범위가 올바르지 않습니다."
            );
        }

        int requestedWidth = Math.max(1, Math.min(width, 1024));
        int requestedHeight = Math.max(1, Math.min(height, 1024));
        double west = mercatorToLongitude(minX);
        double east = mercatorToLongitude(maxX);
        double south = mercatorToLatitude(minY);
        double north = mercatorToLatitude(maxY);
        String bbox = String.format(Locale.US, "%.8f,%.8f,%.8f,%.8f", south, west, north, east);

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("ServiceKey", serviceKey);
        parameters.put("Freq", frequency);
        if (!depthCode.isBlank()) parameters.put("SegCode", depthCode);
        parameters.put("STDG_SGG_CD", administrativeAreaCode);
        parameters.put("Format", "image/png");
        parameters.put("Bbox", bbox);
        parameters.put("width", Integer.toString(requestedWidth));
        parameters.put("height", Integer.toString(requestedHeight));
        parameters.put("transparent", "TRUE");
        parameters.put("srs", srs);

        String requestUrl = appendQuery(wmsUrl, parameters);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "image/png")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FloodMapException(
                        FloodMapException.Type.UPSTREAM,
                        "홍수위험지도 WMS 요청이 실패했습니다."
                );
            }
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.contains("image/png")) {
                throw new FloodMapException(
                        FloodMapException.Type.UPSTREAM,
                        "홍수위험지도 WMS가 이미지 응답을 반환하지 않았습니다."
                );
            }
            return response.body();
        } catch (FloodMapException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FloodMapException(
                    FloodMapException.Type.UPSTREAM,
                    "홍수위험지도 WMS 요청이 중단되었습니다.",
                    exception
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new FloodMapException(
                    FloodMapException.Type.UPSTREAM,
                    "홍수위험지도 WMS 서버에 연결하지 못했습니다.",
                    exception
            );
        }
    }

    /**
     * Returns the geometry object accepted by ORS options.avoid_polygons.
     * M7 currently has one local Polygon; a live provider can replace this
     * implementation without changing RouteService or its API contract.
     */
    public JsonNode getAvoidGeometry() {
        JsonNode features = demoGeoJson.path("features");
        if (!features.isArray() || features.size() == 0) {
            throw new IllegalStateException("FloodZone fixture에 Polygon Feature가 없습니다.");
        }

        JsonNode geometry = features.path(0).path("geometry");
        if (!"Polygon".equals(geometry.path("type").asText())) {
            throw new IllegalStateException("현재 MVP는 Polygon FloodZone만 지원합니다.");
        }
        return geometry;
    }

    private static String normalizeMode(String configuredMode) {
        return "live".equalsIgnoreCase(configuredMode == null ? "" : configuredMode.trim())
                ? "live"
                : "demo";
    }

    private static String appendQuery(String baseUrl, Map<String, String> parameters) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        StringBuilder query = new StringBuilder(baseUrl).append(separator);
        parameters.forEach((key, value) -> {
            if (query.charAt(query.length() - 1) != '?' && query.charAt(query.length() - 1) != '&') {
                query.append('&');
            }
            query.append(encode(key)).append('=').append(encode(value));
        });
        return query.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double mercatorToLongitude(double x) {
        return Math.toDegrees(x / 6378137.0);
    }

    private static double mercatorToLatitude(double y) {
        double latitude = Math.toDegrees(2.0 * Math.atan(Math.exp(y / 6378137.0)) - Math.PI / 2.0);
        return Math.max(-85.05112878, Math.min(85.05112878, latitude));
    }
}

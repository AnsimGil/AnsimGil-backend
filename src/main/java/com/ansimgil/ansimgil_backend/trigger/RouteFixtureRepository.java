package com.ansimgil.ansimgil_backend.trigger;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * M10 제출·재현용 경로 fixture입니다.
 * 정상선과 안전선을 요청된 출발지·목적지에 맞춰 재현합니다.
 */
@Component
public class RouteFixtureRepository {
    private static final String FIXTURE_PATH = "fixtures/m10-routes.json";

    private final JsonNode routes;
    private final ObjectMapper objectMapper;

    public RouteFixtureRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try (InputStream inputStream = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            this.routes = objectMapper.readTree(inputStream);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("M10 경로 fixture를 읽지 못했습니다: " + FIXTURE_PATH, exception);
        }
    }

    public JsonNode find(boolean safe) {
        JsonNode route = routes.path(safe ? "safe" : "normal");
        if (!route.isObject()) {
            throw new IllegalStateException("M10 경로 fixture에 유효한 경로가 없습니다.");
        }
        return route;
    }

    public JsonNode find(
            boolean safe,
            RouteRequest.Coordinate origin,
            RouteRequest.Coordinate destination
    ) {
        if (origin == null || destination == null) return find(safe);

        JsonNode route = find(safe).deepCopy();
        JsonNode feature = route.path("features").path(0);
        JsonNode geometry = feature.path("geometry");
        if (!(geometry instanceof ObjectNode geometryObject)) {
            throw new IllegalStateException("M10 경로 fixture에 유효한 Geometry가 없습니다.");
        }

        ArrayNode coordinates = objectMapper.createArrayNode();
        buildCoordinates(safe, origin, destination).forEach(coordinate ->
                coordinates.add(objectMapper.createArrayNode()
                        .add(coordinate.longitude())
                        .add(coordinate.latitude()))
        );
        geometryObject.set("coordinates", coordinates);
        return route;
    }

    private List<RouteRequest.Coordinate> buildCoordinates(
            boolean safe,
            RouteRequest.Coordinate origin,
            RouteRequest.Coordinate destination
    ) {
        if (!safe) {
            double latitudeDelta = destination.latitude() - origin.latitude();
            double longitudeDelta = destination.longitude() - origin.longitude();
            return List.of(
                    origin,
                    point(origin.latitude() + latitudeDelta * 0.33, origin.longitude() + longitudeDelta * 0.33),
                    point(origin.latitude() + latitudeDelta * 0.67, origin.longitude() + longitudeDelta * 0.67),
                    destination
            );
        }

        // Demo FloodZone bounding box: keep the generated safe line below and
        // to the east of it, while always ending at the requested destination.
        double detourLatitude = Math.min(
                Math.min(origin.latitude(), destination.latitude()),
                37.5668
        ) - 0.003;
        double detourLongitude = Math.max(
                Math.max(origin.longitude(), destination.longitude()),
                126.9885
        ) + 0.002;
        return List.of(
                origin,
                point(detourLatitude, origin.longitude()),
                point(detourLatitude, detourLongitude),
                point(destination.latitude(), detourLongitude),
                destination
        );
    }

    private RouteRequest.Coordinate point(double latitude, double longitude) {
        return new RouteRequest.Coordinate(latitude, longitude);
    }
}

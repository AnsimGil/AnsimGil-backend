package com.ansimgil.ansimgil_backend.trigger;

import com.ansimgil.ansimgil_backend.disaster.DisasterEvent;
import com.ansimgil.ansimgil_backend.disaster.LocationRelevance;
import com.ansimgil.ansimgil_backend.disaster.LocationRelevanceEvent;
import com.ansimgil.ansimgil_backend.disaster.LocationRelevanceResponse;
import com.ansimgil.ansimgil_backend.disaster.LocationRelevanceService;
import com.ansimgil.ansimgil_backend.flood.FloodZoneResponse;
import com.ansimgil.ansimgil_backend.flood.FloodZoneService;
import com.ansimgil.ansimgil_backend.route.RouteRequest;
import com.ansimgil.ansimgil_backend.route.RouteService;
import com.ansimgil.ansimgil_backend.weather.WeatherResponse;
import com.ansimgil.ansimgil_backend.weather.WeatherService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class M10TriggerService {
    private static final String NOTIFICATION_TITLE = "안심길 침수 위험 경고";

    private final LocationRelevanceService locationRelevanceService;
    private final WeatherService weatherService;
    private final FloodZoneService floodZoneService;
    private final RouteService routeService;
    private final RouteFixtureRepository routeFixtureRepository;
    private final ExpoPushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;
    private final String defaultRouteMode;
    private final String defaultDataMode;
    private final String defaultPushMode;

    public M10TriggerService(
            LocationRelevanceService locationRelevanceService,
            WeatherService weatherService,
            FloodZoneService floodZoneService,
            RouteService routeService,
            RouteFixtureRepository routeFixtureRepository,
            ExpoPushNotificationService pushNotificationService,
            ObjectMapper objectMapper,
            @Value("${m10.route-mode:demo}") String defaultRouteMode,
            @Value("${m10.data-mode:demo}") String defaultDataMode,
            @Value("${m10.push-mode:demo}") String defaultPushMode
    ) {
        this.locationRelevanceService = locationRelevanceService;
        this.weatherService = weatherService;
        this.floodZoneService = floodZoneService;
        this.routeService = routeService;
        this.routeFixtureRepository = routeFixtureRepository;
        this.pushNotificationService = pushNotificationService;
        this.objectMapper = objectMapper;
        this.defaultRouteMode = normalizeRouteMode(defaultRouteMode);
        this.defaultDataMode = normalizeDataMode(defaultDataMode);
        this.defaultPushMode = normalizePushMode(defaultPushMode);
    }

    public M10TriggerResponse trigger(M10TriggerRequest request) {
        String routeMode = normalizeRouteMode(request.routeModeOrDefault(defaultRouteMode));
        String dataMode = normalizeDataMode(request.dataModeOrDefault(defaultDataMode));
        String pushMode = normalizePushMode(request.pushModeOrDefault(defaultPushMode));
        LocationRelevanceResponse locationRelevance = locationRelevanceService.getEvents(
                request.location(),
                request.requestedLimit(),
                dataMode
        );

        LocationRelevanceEvent relevantEvent = locationRelevance.events().stream()
                .filter(event -> event.locationRelevance() == LocationRelevance.LOCATION_RELEVANT)
                .findFirst()
                .orElse(null);
        boolean floodRelated = !locationRelevance.events().isEmpty();
        boolean locationRelevant = relevantEvent != null;
        M10TriggerResponse.Decision decision = new M10TriggerResponse.Decision(
                floodRelated,
                locationRelevant,
                relevantEvent == null ? null : relevantEvent.event().id(),
                relevantEvent == null ? null : relevantEvent.matchedRegion()
        );

        if (!locationRelevant) {
            String status = floodRelated ? "LOCATION_NOT_RELEVANT" : "NO_FLOOD_EVENT";
            return new M10TriggerResponse(
                    status,
                    dataMode,
                    decision,
                    locationRelevance,
                    null,
                    null,
                    M10TriggerResponse.RouteStage.skipped(routeMode),
                    M10TriggerResponse.PushStage.skipped(pushMode, "관련 사용자로 판정되지 않아 발송하지 않았습니다.")
            );
        }

        boolean forceDemo = !"live".equals(dataMode);
        WeatherResponse weather = weatherService.getShortTerm(request.location(), forceDemo);
        FloodZoneResponse floodZone = floodZoneService.getFloodZones(forceDemo);
        M10TriggerResponse.RouteStage route = calculateRoute(request, routeMode);
        M10TriggerResponse.PushStage push = sendPush(request, pushMode, dataMode, relevantEvent.event());

        return new M10TriggerResponse(
                "TRIGGERED",
                dataMode,
                decision,
                locationRelevance,
                weather,
                floodZone,
                route,
                push
        );
    }

    private M10TriggerResponse.RouteStage calculateRoute(M10TriggerRequest request, String routeMode) {
        if (request.destination() == null) {
            return M10TriggerResponse.RouteStage.skipped(
                    routeMode,
                    "목적지가 지정되지 않아 재난 Trigger만 처리했습니다. 앱에서 목적지를 선택하면 경로를 계산합니다."
            );
        }

        if ("demo".equals(routeMode)) {
            return new M10TriggerResponse.RouteStage(
                    "READY",
                    routeMode,
                    "LOCAL_FIXTURE",
                    null,
                    routeFixtureRepository.find(true, request.location(), request.destination())
            );
        }

        String rawGeoJson = routeService.requestRoute(toRouteRequest(request), true);
        try {
            JsonNode geoJson = objectMapper.readTree(rawGeoJson);
            return new M10TriggerResponse.RouteStage(
                    "READY",
                    routeMode,
                    "ORS",
                    null,
                    geoJson
            );
        } catch (JacksonException exception) {
            return new M10TriggerResponse.RouteStage(
                    "FAILED",
                    routeMode,
                    "ORS",
                    "ORS 응답을 GeoJSON으로 해석하지 못했습니다.",
                    null
            );
        }
    }

    private M10TriggerResponse.PushStage sendPush(
            M10TriggerRequest request,
            String pushMode,
            String dataMode,
            DisasterEvent event
    ) {
        if (!request.sendPush()) {
            return M10TriggerResponse.PushStage.skipped(pushMode, "sendPush=false이므로 발송하지 않았습니다.");
        }

        String notificationUrl = notificationUrl(request);
        String body = "현재 위치와 관련된 침수 위험이 감지되었습니다. 침수 위험지역을 우회합니다.";
        if ("demo".equals(pushMode)) {
            return new M10TriggerResponse.PushStage(
                    true,
                    pushMode,
                    "SIMULATED",
                    "Demo 모드에서 푸시 발송을 시뮬레이션했습니다.\n대상 이벤트: " + event.id(),
                    notificationUrl
            );
        }

        ExpoPushNotificationService.SendResult result = pushNotificationService.send(
                request.pushToken(),
                NOTIFICATION_TITLE,
                body,
                notificationUrl,
                dataMode
        );
        return new M10TriggerResponse.PushStage(
                true,
                pushMode,
                result.success() ? "SENT" : "FAILED",
                result.message(),
                notificationUrl
        );
    }

    private String notificationUrl(M10TriggerRequest request) {
        StringBuilder url = new StringBuilder("/?floodAlert=true")
                .append("&originLatitude=").append(request.location().latitude())
                .append("&originLongitude=").append(request.location().longitude());
        if (request.destination() != null) {
            url.append("&destinationLatitude=").append(request.destination().latitude())
                    .append("&destinationLongitude=").append(request.destination().longitude());
        }
        return url.toString();
    }

    private RouteRequest toRouteRequest(M10TriggerRequest request) {
        return new RouteRequest(request.location(), request.destination());
    }

    private static String normalizeRouteMode(String value) {
        return "ors".equalsIgnoreCase(value == null ? "" : value.trim()) ? "ors" : "demo";
    }

    private static String normalizePushMode(String value) {
        return "live".equalsIgnoreCase(value == null ? "" : value.trim()) ? "live" : "demo";
    }

    private static String normalizeDataMode(String value) {
        String normalized = value == null ? "" : value.trim();
        if ("live".equalsIgnoreCase(normalized)) return "live";
        if ("test".equalsIgnoreCase(normalized)) return "test";
        return "demo";
    }
}

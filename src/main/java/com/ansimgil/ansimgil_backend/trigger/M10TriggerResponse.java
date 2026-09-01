package com.ansimgil.ansimgil_backend.trigger;

import com.ansimgil.ansimgil_backend.disaster.LocationRelevanceResponse;
import com.ansimgil.ansimgil_backend.flood.FloodZoneResponse;
import com.ansimgil.ansimgil_backend.weather.WeatherResponse;
import tools.jackson.databind.JsonNode;

public record M10TriggerResponse(
        String triggerStatus,
        String dataMode,
        Decision decision,
        LocationRelevanceResponse locationRelevance,
        WeatherResponse weather,
        FloodZoneResponse floodZone,
        RouteStage route,
        PushStage push
) {
    public record Decision(
            boolean floodRelated,
            boolean locationRelevant,
            String eventId,
            String matchedRegion
    ) {
    }

    public record RouteStage(
            String status,
            String mode,
            String source,
            String error,
            JsonNode geoJson
    ) {
        public static RouteStage skipped(String mode) {
            return new RouteStage("SKIPPED", mode, null, null, null);
        }

        public static RouteStage skipped(String mode, String error) {
            return new RouteStage("SKIPPED", mode, null, error, null);
        }
    }

    public record PushStage(
            boolean requested,
            String mode,
            String status,
            String message,
            String notificationUrl
    ) {
        public static PushStage skipped(String mode, String message) {
            return new PushStage(false, mode, "SKIPPED", message, null);
        }
    }
}

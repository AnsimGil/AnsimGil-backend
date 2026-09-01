package com.ansimgil.ansimgil_backend.flood;

import tools.jackson.databind.JsonNode;

public record FloodZoneResponse(
        String source,
        String requestedMode,
        boolean live,
        String fallbackReason,
        JsonNode geoJson
) {
}

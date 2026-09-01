package com.ansimgil.ansimgil_backend.disaster;

import com.ansimgil.ansimgil_backend.route.RouteRequest;

import java.util.List;

public record LocationRelevanceResponse(
        String source,
        boolean live,
        RouteRequest.Coordinate userLocation,
        boolean hasRelevantEvent,
        List<LocationRelevanceEvent> events
) {
}

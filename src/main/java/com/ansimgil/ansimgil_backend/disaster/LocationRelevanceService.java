package com.ansimgil.ansimgil_backend.disaster;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocationRelevanceService {
    private final DisasterService disasterService;
    private final FloodMessageClassifier floodMessageClassifier;
    private final SeoulRegionMatcher seoulRegionMatcher;

    public LocationRelevanceService(
            DisasterService disasterService,
            FloodMessageClassifier floodMessageClassifier,
            SeoulRegionMatcher seoulRegionMatcher
    ) {
        this.disasterService = disasterService;
        this.floodMessageClassifier = floodMessageClassifier;
        this.seoulRegionMatcher = seoulRegionMatcher;
    }

    public LocationRelevanceResponse getEvents(RouteRequest.Coordinate location, int limit) {
        return getEvents(location, limit, false);
    }

    public LocationRelevanceResponse getEvents(
            RouteRequest.Coordinate location,
            int limit,
            boolean forceDemo
    ) {
        DisasterResponse floodEvents = disasterService.getEvents(limit, forceDemo);
        List<LocationRelevanceEvent> evaluatedEvents = floodEvents.events().stream()
                .filter(floodMessageClassifier::isFloodRelated)
                .map(event -> evaluate(event, location))
                .toList();

        boolean hasRelevantEvent = evaluatedEvents.stream()
                .anyMatch(event -> event.locationRelevance() == LocationRelevance.LOCATION_RELEVANT);

        return new LocationRelevanceResponse(
                floodEvents.source(),
                floodEvents.live(),
                location,
                hasRelevantEvent,
                evaluatedEvents
        );
    }

    public LocationRelevanceResponse getEvents(
            RouteRequest.Coordinate location,
            int limit,
            String dataMode
    ) {
        DisasterResponse floodEvents = disasterService.getEventsForMode(limit, dataMode);
        List<LocationRelevanceEvent> evaluatedEvents = floodEvents.events().stream()
                .filter(floodMessageClassifier::isFloodRelated)
                .map(event -> evaluate(event, location))
                .toList();

        boolean hasRelevantEvent = evaluatedEvents.stream()
                .anyMatch(event -> event.locationRelevance() == LocationRelevance.LOCATION_RELEVANT);

        return new LocationRelevanceResponse(
                floodEvents.source(),
                floodEvents.live(),
                location,
                hasRelevantEvent,
                evaluatedEvents
        );
    }

    private LocationRelevanceEvent evaluate(DisasterEvent event, RouteRequest.Coordinate location) {
        SeoulRegionMatcher.MatchResult result = seoulRegionMatcher.match(event.region(), location);
        return new LocationRelevanceEvent(
                event,
                result.relevance(),
                result.matchMethod(),
                result.matchedRegion()
        );
    }
}

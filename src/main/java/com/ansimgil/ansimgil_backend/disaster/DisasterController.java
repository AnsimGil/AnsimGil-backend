package com.ansimgil.ansimgil_backend.disaster;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/disasters")
public class DisasterController {
    private final DisasterService disasterService;
    private final FloodMessageClassifier floodMessageClassifier;
    private final LocationRelevanceService locationRelevanceService;

    public DisasterController(
            DisasterService disasterService,
            FloodMessageClassifier floodMessageClassifier,
            LocationRelevanceService locationRelevanceService
    ) {
        this.disasterService = disasterService;
        this.floodMessageClassifier = floodMessageClassifier;
        this.locationRelevanceService = locationRelevanceService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public DisasterResponse getDisasters(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return disasterService.getEvents(limit);
    }

    @GetMapping(value = "/flood-related", produces = MediaType.APPLICATION_JSON_VALUE)
    public DisasterResponse getFloodRelatedDisasters(
            @RequestParam(defaultValue = "20") int limit
    ) {
        DisasterResponse response = disasterService.getEvents(limit);
        return new DisasterResponse(
                response.source(),
                response.live(),
                response.events().stream()
                        .filter(floodMessageClassifier::isFloodRelated)
                        .toList()
        );
    }

    @PostMapping(value = "/location-relevant", produces = MediaType.APPLICATION_JSON_VALUE)
    public LocationRelevanceResponse getLocationRelevantDisasters(
            @Valid @RequestBody LocationRelevanceRequest request
    ) {
        return locationRelevanceService.getEvents(
                request.location(),
                request.requestedLimit()
        );
    }
}

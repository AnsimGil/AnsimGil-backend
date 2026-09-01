package com.ansimgil.ansimgil_backend.disaster;

public record LocationRelevanceEvent(
        DisasterEvent event,
        LocationRelevance locationRelevance,
        String matchMethod,
        String matchedRegion
) {
}

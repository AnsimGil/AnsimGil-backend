package com.ansimgil.ansimgil_backend.place;

import java.util.List;

public record PlaceSearchResponse(
        String source,
        boolean live,
        List<PlaceSuggestion> suggestions
) {
}

package com.ansimgil.ansimgil_backend.place;

import java.util.List;

public record PlaceSuggestion(
        String placeId,
        String primaryText,
        String secondaryText,
        String fullText,
        List<String> types
) {
}

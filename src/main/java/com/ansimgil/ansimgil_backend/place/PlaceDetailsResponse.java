package com.ansimgil.ansimgil_backend.place;

import com.ansimgil.ansimgil_backend.route.RouteRequest;

public record PlaceDetailsResponse(
        String source,
        boolean live,
        String placeId,
        String name,
        String address,
        RouteRequest.Coordinate location
) {
}

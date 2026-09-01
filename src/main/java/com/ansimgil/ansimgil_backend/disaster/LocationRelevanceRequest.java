package com.ansimgil.ansimgil_backend.disaster;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LocationRelevanceRequest(
        @NotNull @Valid RouteRequest.Coordinate location,
        @Min(1) @Max(100) Integer limit
) {
    public int requestedLimit() {
        return limit == null ? 20 : limit;
    }
}

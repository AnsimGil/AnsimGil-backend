package com.ansimgil.ansimgil_backend.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RouteRequest(
        @NotNull @Valid Coordinate origin,
        @NotNull @Valid Coordinate destination
) {
    public record Coordinate(
            @NotNull
            @DecimalMin(value = "-90.0")
            @DecimalMax(value = "90.0")
            Double latitude,
            @NotNull
            @DecimalMin(value = "-180.0")
            @DecimalMax(value = "180.0")
            Double longitude
    ) {
        public List<Double> asGeoJsonPosition() {
            return List.of(longitude, latitude);
        }
    }
}

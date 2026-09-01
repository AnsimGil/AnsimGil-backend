package com.ansimgil.ansimgil_backend.route;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestNormalRoute(@Valid @RequestBody RouteRequest request) {
        return geoJson(routeService.requestRoute(request, false));
    }

    @PostMapping(value = "/safe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestSafeRoute(@Valid @RequestBody RouteRequest request) {
        return geoJson(routeService.requestRoute(request, true));
    }

    private ResponseEntity<String> geoJson(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

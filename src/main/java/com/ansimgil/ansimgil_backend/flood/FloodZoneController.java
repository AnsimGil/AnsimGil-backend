package com.ansimgil.ansimgil_backend.flood;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flood-zones")
public class FloodZoneController {
    private final FloodZoneService floodZoneService;

    public FloodZoneController(FloodZoneService floodZoneService) {
        this.floodZoneService = floodZoneService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public FloodZoneResponse getFloodZones() {
        return floodZoneService.getFloodZones();
    }

    @GetMapping(value = "/wms-tile", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getWmsTile(
            @RequestParam double minX,
            @RequestParam double maxX,
            @RequestParam double minY,
            @RequestParam double maxY,
            @RequestParam(defaultValue = "256") int width,
            @RequestParam(defaultValue = "256") int height
    ) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(floodZoneService.fetchWmsTile(minX, maxX, minY, maxY, width, height));
        } catch (FloodMapException exception) {
            HttpStatus status = switch (exception.type()) {
                case CONFIGURATION -> HttpStatus.SERVICE_UNAVAILABLE;
                case REQUEST -> HttpStatus.BAD_REQUEST;
                case UPSTREAM -> HttpStatus.BAD_GATEWAY;
            };
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(exception.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}

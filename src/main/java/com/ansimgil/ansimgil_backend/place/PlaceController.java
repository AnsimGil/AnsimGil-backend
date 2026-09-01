package com.ansimgil.ansimgil_backend.place;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/autocomplete")
    public PlaceSearchResponse autocomplete(@RequestParam("input") String input) {
        return placeService.autocomplete(input);
    }

    @GetMapping("/details")
    public PlaceDetailsResponse details(@RequestParam("placeId") String placeId) {
        return placeService.details(placeId);
    }
}

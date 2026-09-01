package com.ansimgil.ansimgil_backend.disaster;

import java.util.List;

public record DisasterResponse(
        String source,
        boolean live,
        List<DisasterEvent> events
) {
}

package com.ansimgil.ansimgil_backend.trigger;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class M10TriggerController {
    private final M10TriggerService triggerService;

    public M10TriggerController(M10TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * M10 전체 재난 Trigger를 로컬에서 실행합니다.
     * 기본값은 route=demo, push=demo라서 외부 키 없이 재현됩니다.
     */
    @PostMapping(value = "/trigger", produces = MediaType.APPLICATION_JSON_VALUE)
    public M10TriggerResponse trigger(@Valid @RequestBody M10TriggerRequest request) {
        return triggerService.trigger(request);
    }
}

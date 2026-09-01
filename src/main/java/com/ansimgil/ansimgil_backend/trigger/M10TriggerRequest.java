package com.ansimgil.ansimgil_backend.trigger;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

/**
 * M10 로컬 재난 Trigger 요청입니다.
 *
 * <p>기본값은 외부 키가 필요 없는 demo 모드입니다. 개인 시연에서는
 * dataMode=live로 실제 재난문자를 사용하거나 dataMode=test로 서울 중구
 * 위험 대상자 fixture를 재현할 수 있습니다. routeMode=ors, pushMode=live도
 * 요청 단위로 선택할 수 있습니다. 목적지를 생략하면 재난·위치 판정과 푸시만
 * 처리하고, 앱에서 목적지를 선택한 뒤 경로를 별도로 계산합니다.</p>
 */
public record M10TriggerRequest(
        @NotNull @Valid RouteRequest.Coordinate location,
        @Valid RouteRequest.Coordinate destination,
        @Min(1) @Max(100) Integer limit,
        @Pattern(regexp = "(?i)demo|ors", message = "routeMode은 demo 또는 ors여야 합니다.")
        String routeMode,
        @Pattern(regexp = "(?i)demo|live|test", message = "dataMode은 demo, live 또는 test여야 합니다.")
        String dataMode,
        @Pattern(regexp = "(?i)demo|live", message = "pushMode은 demo 또는 live여야 합니다.")
        String pushMode,
        boolean sendPush,
        String pushToken
) {
    public int requestedLimit() {
        return limit == null ? 20 : limit;
    }

    public String routeModeOrDefault(String fallback) {
        return routeMode == null || routeMode.isBlank() ? fallback : routeMode;
    }

    public String pushModeOrDefault(String fallback) {
        return pushMode == null || pushMode.isBlank() ? fallback : pushMode;
    }

    public String dataModeOrDefault(String fallback) {
        return dataMode == null || dataMode.isBlank() ? fallback : dataMode;
    }
}

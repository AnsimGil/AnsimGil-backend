package com.ansimgil.ansimgil_backend.disaster;

/**
 * AnsimGil에서 사용하는 재난문자 표준 모델입니다.
 *
 * <p>공공 API의 원본 필드명은 제공기관이나 API 버전에 따라 달라질 수 있으므로,
 * 앱과 백엔드 사이에서는 이 모델만 사용합니다.</p>
 */
public record DisasterEvent(
        String id,
        String receivedAt,
        String type,
        String title,
        String message,
        String region,
        String source
) {
}

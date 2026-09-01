package com.ansimgil.ansimgil_backend.trigger;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expo Push Service의 최소 발송 어댑터입니다.
 * Expo Push Token과 Firebase 서비스 계정 키는 응답·로그·소스에 기록하지 않습니다.
 */
@Service
public class ExpoPushNotificationService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public ExpoPushNotificationService(
            ObjectMapper objectMapper,
            @Value("${expo-push.base-url:https://exp.host/--/api/v2/push/send}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl.trim();
    }

    public SendResult send(
            String pushToken,
            String title,
            String body,
            String notificationUrl,
            String dataMode
    ) {
        if (pushToken == null || pushToken.isBlank()) {
            return SendResult.failure("푸시 토큰이 없어 발송하지 않았습니다.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", pushToken.trim());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("sound", "default");
        payload.put("priority", "high");
        payload.put("channelId", "safety-alerts");
        payload.put("data", Map.of(
                "url", notificationUrl,
                "trigger", "M10_FLOOD_ALERT",
                "dataMode", dataMode
        ));

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SendResult.failure("Expo Push Service 요청이 실패했습니다 (HTTP " + response.statusCode() + ").");
            }

            return parseResult(response.body());
        } catch (JacksonException exception) {
            return SendResult.failure("푸시 요청 본문을 생성하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.failure("푸시 요청이 중단되었습니다.");
        } catch (IOException | IllegalArgumentException exception) {
            return SendResult.failure("Expo Push Service에 연결하지 못했습니다.");
        }
    }

    private SendResult parseResult(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String status = response.path("data").path("status").asText("");
            if ("ok".equalsIgnoreCase(status)) {
                return SendResult.success("Expo Push Service에 발송 요청을 전달했습니다.");
            }
            if ("error".equalsIgnoreCase(status)) {
                return SendResult.failure("Expo Push Service가 발송을 거부했습니다.");
            }
            return SendResult.success("Expo Push Service가 요청을 접수했습니다.");
        } catch (JacksonException exception) {
            return SendResult.success("Expo Push Service에 발송 요청을 전달했습니다.");
        }
    }

    public record SendResult(boolean success, String message) {
        static SendResult success(String message) {
            return new SendResult(true, message);
        }

        static SendResult failure(String message) {
            return new SendResult(false, message);
        }
    }
}

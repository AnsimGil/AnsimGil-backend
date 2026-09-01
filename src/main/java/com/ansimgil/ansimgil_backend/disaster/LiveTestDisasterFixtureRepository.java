package com.ansimgil.ansimgil_backend.disaster;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 실제 행정안전부 LIVE 조회와 분리된 M10 위험 대상자 시연용 fixture입니다.
 * source를 LIVE_TEST_FIXTURE로 유지해 실제 공공데이터와 혼동하지 않습니다.
 */
@Component
public class LiveTestDisasterFixtureRepository {
    private static final String FIXTURE_PATH = "fixtures/live-test-disaster-messages.json";

    private final List<DisasterEvent> events;

    public LiveTestDisasterFixtureRepository(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            DisasterEvent[] loaded = objectMapper.readValue(inputStream, DisasterEvent[].class);
            this.events = List.of(loaded);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("LIVE 테스트 재난문자 fixture를 읽지 못했습니다: " + FIXTURE_PATH, exception);
        }
    }

    public List<DisasterEvent> findAll() {
        return events;
    }
}

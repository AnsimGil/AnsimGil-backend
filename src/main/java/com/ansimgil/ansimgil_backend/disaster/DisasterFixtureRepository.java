package com.ansimgil.ansimgil_backend.disaster;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class DisasterFixtureRepository {
    private static final String FIXTURE_PATH = "fixtures/disaster-messages.json";

    private final List<DisasterEvent> events;

    public DisasterFixtureRepository(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            DisasterEvent[] loaded = objectMapper.readValue(inputStream, DisasterEvent[].class);
            this.events = List.of(loaded);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("재난문자 fixture를 읽지 못했습니다: " + FIXTURE_PATH, exception);
        }
    }

    public List<DisasterEvent> findAll() {
        return events;
    }
}

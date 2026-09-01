package com.ansimgil.ansimgil_backend.disaster;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Comparator;

@Service
public class DisasterService {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SLASH_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter DASH_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DisasterMessageClient client;
    private final DisasterNormalizer normalizer;
    private final DisasterFixtureRepository fixtureRepository;
    private final LiveTestDisasterFixtureRepository liveTestFixtureRepository;
    private final boolean useLive;
    private final int maxRows;
    private final int lookbackDays;

    public DisasterService(
            DisasterMessageClient client,
            DisasterNormalizer normalizer,
            DisasterFixtureRepository fixtureRepository,
            LiveTestDisasterFixtureRepository liveTestFixtureRepository,
            @Value("${disaster-data.use-live:false}") boolean useLive,
            @Value("${disaster-data.max-rows:20}") int maxRows,
            @Value("${disaster-data.lookback-days:1}") int lookbackDays
    ) {
        this.client = client;
        this.normalizer = normalizer;
        this.fixtureRepository = fixtureRepository;
        this.liveTestFixtureRepository = liveTestFixtureRepository;
        this.useLive = useLive;
        this.maxRows = Math.max(1, maxRows);
        this.lookbackDays = Math.max(0, lookbackDays);
    }

    public DisasterResponse getEvents(int limit) {
        return getEvents(limit, false);
    }

    /**
     * M10이 전역 LIVE 설정과 무관하게 재현 가능한 시나리오를 선택할 수 있도록 합니다.
     */
    public DisasterResponse getEvents(int limit, boolean forceDemo) {
        int boundedLimit = Math.max(1, Math.min(limit, maxRows));

        if (forceDemo || !useLive) {
            return new DisasterResponse(
                    "LOCAL_FIXTURE",
                    false,
                    fixtureRepository.findAll().stream().limit(boundedLimit).toList()
            );
        }

        LocalDate startDate = LocalDate.now(KOREA_ZONE).minusDays(lookbackDays);
        List<DisasterEvent> events = normalizer.normalize(
                client.fetch(1, maxRows, startDate)
        );

        return new DisasterResponse(
                "SAFETY_DATA",
                true,
                events.stream()
                        .sorted(Comparator.comparing(
                                DisasterService::receivedAt,
                                Comparator.reverseOrder()
                        ))
                        .limit(boundedLimit)
                        .toList()
        );
    }

    /**
     * M10 요청 단위 데이터 모드입니다. test는 실제 LIVE API를 호출하지 않고
     * 서울 중구 대상자 fixture만 사용해 위험 대상자·푸시 흐름을 재현합니다.
     */
    public DisasterResponse getEventsForMode(int limit, String mode) {
        if ("test".equalsIgnoreCase(mode)) {
            int boundedLimit = Math.max(1, Math.min(limit, maxRows));
            return new DisasterResponse(
                    "LIVE_TEST_FIXTURE",
                    false,
                    liveTestFixtureRepository.findAll().stream().limit(boundedLimit).toList()
            );
        }
        return getEvents(limit, "demo".equalsIgnoreCase(mode));
    }

    private static LocalDateTime receivedAt(DisasterEvent event) {
        String value = event.receivedAt();
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Continue with the two formats used by the public API and local fixture.
        }
        try {
            return LocalDateTime.parse(value, SLASH_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Continue with the ISO local format.
        }
        try {
            return LocalDateTime.parse(value, DASH_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Unknown timestamps are placed after parseable events.
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.MIN;
        }
    }
}

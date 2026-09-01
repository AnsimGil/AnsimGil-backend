package com.ansimgil.ansimgil_backend.weather;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WeatherService {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");
    private static final List<LocalTime> BASE_TIMES = List.of(
            LocalTime.of(2, 0),
            LocalTime.of(5, 0),
            LocalTime.of(8, 0),
            LocalTime.of(11, 0),
            LocalTime.of(14, 0),
            LocalTime.of(17, 0),
            LocalTime.of(20, 0),
            LocalTime.of(23, 0)
    );

    private final WeatherForecastClient client;
    private final WeatherForecastNormalizer normalizer;
    private final WeatherFixtureRepository fixtureRepository;
    private final KmaGridConverter gridConverter;
    private final WeatherRiskEvaluator riskEvaluator;
    private final boolean useLive;

    public WeatherService(
            WeatherForecastClient client,
            WeatherForecastNormalizer normalizer,
            WeatherFixtureRepository fixtureRepository,
            KmaGridConverter gridConverter,
            WeatherRiskEvaluator riskEvaluator,
            @Value("${kma-weather.use-live:false}") boolean useLive
    ) {
        this.client = client;
        this.normalizer = normalizer;
        this.fixtureRepository = fixtureRepository;
        this.gridConverter = gridConverter;
        this.riskEvaluator = riskEvaluator;
        this.useLive = useLive;
    }

    public WeatherResponse getShortTerm(RouteRequest.Coordinate location) {
        return getShortTerm(location, false);
    }

    /**
     * M10 Demo가 전역 KMA LIVE 설정에 영향을 받지 않도록 fixture를 명시적으로 선택합니다.
     */
    public WeatherResponse getShortTerm(RouteRequest.Coordinate location, boolean forceDemo) {
        KmaGridPoint grid = gridConverter.convert(location);
        ForecastBase base = resolveBaseTime(ZonedDateTime.now(KOREA_ZONE));

        if (forceDemo || !useLive) {
            return response(
                    "LOCAL_FIXTURE",
                    false,
                    location,
                    grid,
                    base,
                    fixtureRepository.findAll(),
                    "LIVE_DISABLED"
            );
        }

        try {
            List<WeatherForecast> forecasts = normalizer.normalize(
                    client.fetch(grid, base.date(), base.time())
            );
            if (forecasts.isEmpty()) {
                throw new WeatherException(
                        WeatherException.Type.UPSTREAM,
                        "기상청 단기예보 응답에 예보 항목이 없습니다."
                );
            }
            return response("KMA_WEATHER", true, location, grid, base, forecasts, null);
        } catch (WeatherException exception) {
            // 예보는 Risk Context이므로 API 장애가 재난 Trigger를 차단하지 않도록 fixture로 전환합니다.
            return response(
                    "LOCAL_FIXTURE",
                    false,
                    location,
                    grid,
                    base,
                    fixtureRepository.findAll(),
                    exception.type() == WeatherException.Type.CONFIGURATION
                            ? "LIVE_CONFIGURATION_MISSING"
                            : "LIVE_API_UNAVAILABLE"
            );
        }
    }

    ForecastBase resolveBaseTime(ZonedDateTime now) {
        ZonedDateTime availableAt = now.minusMinutes(10);
        LocalTime selected = BASE_TIMES.stream()
                .filter(time -> !time.isAfter(availableAt.toLocalTime()))
                .reduce((first, second) -> second)
                .orElse(LocalTime.of(23, 0));
        LocalDate date = availableAt.toLocalDate();
        if (BASE_TIMES.stream().noneMatch(time -> !time.isAfter(availableAt.toLocalTime()))) {
            date = date.minusDays(1);
        }
        return new ForecastBase(date.format(DATE_FORMAT), selected.format(TIME_FORMAT));
    }

    private WeatherResponse response(
            String source,
            boolean live,
            RouteRequest.Coordinate location,
            KmaGridPoint grid,
            ForecastBase base,
            List<WeatherForecast> forecasts,
            String fallbackReason
    ) {
        WeatherRiskLevel riskLevel = riskEvaluator.evaluate(forecasts);
        return new WeatherResponse(
                source,
                live,
                location,
                grid,
                base.date(),
                base.time(),
                riskLevel,
                riskEvaluator.summarize(riskLevel),
                fallbackReason,
                forecasts
        );
    }

    record ForecastBase(String date, String time) {
    }
}

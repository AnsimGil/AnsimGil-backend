package com.ansimgil.ansimgil_backend.disaster;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M6의 로컬 행정구역·사용자 위치 비교기입니다.
 *
 * <p>MVP에서는 지오코딩 API나 행정구역 경계 데이터에 의존하지 않고,
 * 서울시와 서울 25개 구의 재현 가능한 근사 bounding box를 사용합니다.
 * 실제 운영 전에는 공식 행정구역 polygon 또는 주소/좌표 변환 데이터로 교체합니다.</p>
 */
@Component
public class SeoulRegionMatcher {
    private static final Set<String> SEOUL_ALIASES = Set.of("서울특별시", "서울시", "서울");

    private static final RegionBox SEOUL = new RegionBox(
            "서울특별시",
            Set.of("서울특별시", "서울시", "서울"),
            37.413, 37.715, 126.734, 127.269
    );

    // 서울시 구별 근사 범위입니다. M6 재현용이며 공식 법정동 경계를 의미하지 않습니다.
    private static final List<RegionBox> SEOUL_DISTRICTS = List.of(
            box("서울특별시 중구", 37.548, 37.575, 126.965, 127.015, "중구"),
            box("서울특별시 종로구", 37.570, 37.630, 126.950, 127.020, "종로구"),
            box("서울특별시 용산구", 37.515, 37.555, 126.950, 127.020, "용산구"),
            box("서울특별시 성동구", 37.535, 37.575, 127.015, 127.070, "성동구"),
            box("서울특별시 광진구", 37.530, 37.580, 127.060, 127.130, "광진구"),
            box("서울특별시 동대문구", 37.560, 37.620, 127.020, 127.090, "동대문구"),
            box("서울특별시 중랑구", 37.580, 37.650, 127.060, 127.150, "중랑구"),
            box("서울특별시 성북구", 37.580, 37.650, 126.980, 127.070, "성북구"),
            box("서울특별시 강북구", 37.620, 37.700, 126.970, 127.050, "강북구"),
            box("서울특별시 도봉구", 37.650, 37.720, 126.960, 127.070, "도봉구"),
            box("서울특별시 노원구", 37.620, 37.700, 127.060, 127.150, "노원구"),
            box("서울특별시 은평구", 37.580, 37.670, 126.890, 127.000, "은평구"),
            box("서울특별시 서대문구", 37.550, 37.600, 126.900, 126.980, "서대문구"),
            box("서울특별시 마포구", 37.530, 37.590, 126.880, 126.960, "마포구"),
            box("서울특별시 양천구", 37.510, 37.560, 126.820, 126.890, "양천구"),
            box("서울특별시 강서구", 37.540, 37.620, 126.770, 126.900, "강서구"),
            box("서울특별시 구로구", 37.460, 37.540, 126.810, 126.900, "구로구"),
            box("서울특별시 금천구", 37.430, 37.490, 126.870, 126.930, "금천구"),
            box("서울특별시 영등포구", 37.490, 37.540, 126.880, 126.950, "영등포구"),
            box("서울특별시 동작구", 37.480, 37.540, 126.930, 126.990, "동작구"),
            box("서울특별시 관악구", 37.430, 37.500, 126.910, 126.980, "관악구"),
            box("서울특별시 서초구", 37.450, 37.520, 126.970, 127.080, "서초구"),
            box("서울특별시 강남구", 37.470, 37.540, 127.020, 127.120, "강남구"),
            box("서울특별시 송파구", 37.490, 37.560, 127.080, 127.180, "송파구"),
            box("서울특별시 강동구", 37.520, 37.590, 127.110, 127.180, "강동구")
    );

    public MatchResult match(String region, RouteRequest.Coordinate location) {
        if (region == null || region.isBlank()) {
            return MatchResult.notRelevant("REGION_MISSING");
        }

        for (String segment : splitRegion(region)) {
            String normalizedSegment = normalize(segment);
            if (!containsAny(normalizedSegment, SEOUL_ALIASES)) {
                continue;
            }

            RegionBox district = SEOUL_DISTRICTS.stream()
                    .filter(candidate -> containsAny(normalizedSegment, candidate.aliases()))
                    .findFirst()
                    .orElse(null);

            if (district != null) {
                if (district.contains(location)) {
                    return MatchResult.relevant(district.name(), "SEOUL_DISTRICT_BOUNDS");
                }
                continue;
            }

            if (SEOUL.contains(location)) {
                return MatchResult.relevant(SEOUL.name(), "SEOUL_CITY_BOUNDS");
            }
        }

        return MatchResult.notRelevant("REGION_NOT_MATCHED");
    }

    private static RegionBox box(
            String name,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            String... aliases
    ) {
        return new RegionBox(
                name,
                Set.of(aliases),
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude
        );
    }

    private List<String> splitRegion(String region) {
        return Arrays.stream(region.split("[,，/;；]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean containsAny(String value, Set<String> candidates) {
        return candidates.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record MatchResult(
            LocationRelevance relevance,
            String matchMethod,
            String matchedRegion
    ) {
        public static MatchResult relevant(String matchedRegion, String matchMethod) {
            return new MatchResult(LocationRelevance.LOCATION_RELEVANT, matchMethod, matchedRegion);
        }

        public static MatchResult notRelevant(String matchMethod) {
            return new MatchResult(LocationRelevance.LOCATION_NOT_RELEVANT, matchMethod, null);
        }
    }

    private record RegionBox(
            String name,
            Set<String> aliases,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude
    ) {
        private boolean contains(RouteRequest.Coordinate location) {
            return location.latitude() >= minLatitude
                    && location.latitude() <= maxLatitude
                    && location.longitude() >= minLongitude
                    && location.longitude() <= maxLongitude;
        }
    }
}

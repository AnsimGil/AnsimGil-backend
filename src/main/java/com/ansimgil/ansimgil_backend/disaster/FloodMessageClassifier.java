package com.ansimgil.ansimgil_backend.disaster;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M5의 결정적 홍수·침수 관련성 판별기입니다.
 *
 * <p>정교한 NLP 대신 공식 재난유형과 고신뢰 침수어, 호우와 위험상황의 조합을 사용합니다.
 * 넓은 의미의 단어 하나(예: 호우, 하천, 도로)만으로는 통과시키지 않습니다.</p>
 */
@Component
public class FloodMessageClassifier {
    private static final Set<String> FLOOD_TYPES = Set.of("홍수", "침수", "범람");
    private static final Set<String> HIGH_CONFIDENCE_TERMS = Set.of(
            "침수", "홍수", "범람", "침수위험", "침수피해", "범람위험"
    );
    private static final Set<String> HEAVY_RAIN_TERMS = Set.of(
            "호우", "집중호우", "폭우", "강한비", "많은비"
    );
    private static final Set<String> IMPACT_TERMS = Set.of(
            "하천", "계곡", "저지대", "하상주차장", "둔치", "지하차도",
            "도로", "배수로", "위험지역", "통제", "대피", "접근자제", "이동주차"
    );
    private static final List<Set<String>> EXPLICIT_COMBINATION_RULES = List.of(
            Set.of("지하차도", "통제"),
            Set.of("도로", "통제"),
            Set.of("하상주차장", "이동주차"),
            Set.of("둔치", "차량"),
            Set.of("하천", "접근"),
            Set.of("하천", "대피")
    );

    public FloodRelevance classify(DisasterEvent event) {
        String type = normalize(event.type());
        String message = normalize(event.message());
        String combined = type + " " + message;

        // 공식 재난유형이 홍수·침수·범람이면 구조화된 강한 신호로 인정합니다.
        if (FLOOD_TYPES.contains(type)) {
            return FloodRelevance.FLOOD_RELATED;
        }

        // 메시지의 고신뢰 침수어는 단순 기상 표현보다 구체적인 재난 신호입니다.
        if (containsAny(message, HIGH_CONFIDENCE_TERMS)) {
            return FloodRelevance.FLOOD_RELATED;
        }

        // 호우/폭우와 실제 위험 장소·행동이 함께 있어야 통과시킵니다.
        if (containsAny(combined, HEAVY_RAIN_TERMS)
                && containsAny(combined, IMPACT_TERMS)) {
            return FloodRelevance.FLOOD_RELATED;
        }

        // 기상 표현이 생략된 지하차도 통제 등의 명시적 재난 조합도 인정합니다.
        for (Set<String> rule : EXPLICIT_COMBINATION_RULES) {
            if (rule.stream().allMatch(combined::contains)) {
                return FloodRelevance.FLOOD_RELATED;
            }
        }

        return FloodRelevance.NOT_RELATED;
    }

    public boolean isFloodRelated(DisasterEvent event) {
        return classify(event) == FloodRelevance.FLOOD_RELATED;
    }

    private boolean containsAny(String value, Set<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

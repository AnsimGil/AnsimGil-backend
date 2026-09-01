package com.ansimgil.ansimgil_backend.disaster;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 안전데이터공유플랫폼 응답을 AnsimGil 표준 이벤트로 변환합니다.
 *
 * <p>공공 API의 응답 envelope와 필드명 변경에 대비해 대표적인 영문/한글 필드 별칭을
 * 허용합니다. 실제 키 입력 후 첫 라이브 응답을 확인하면 별칭을 좁혀 고정할 수 있습니다.</p>
 */
@Component
public class DisasterNormalizer {
    private static final List<String> ITEM_KEYS = List.of(
            "items", "item", "data", "records", "results", "result", "body", "response"
    );

    private final ObjectMapper objectMapper;

    public DisasterNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DisasterEvent> normalize(String rawJson) {
        try {
            Object root = objectMapper.readValue(rawJson, Object.class);
            List<Map<?, ?>> rows = findRows(root);
            List<DisasterEvent> events = new ArrayList<>();

            for (int index = 0; index < rows.size(); index++) {
                events.add(normalizeRow(rows.get(index), index));
            }

            return events;
        } catch (JacksonException | ClassCastException exception) {
            throw new DisasterException(
                    DisasterException.Type.INTERNAL,
                    "긴급재난문자 응답을 표준 형식으로 변환하지 못했습니다.",
                    exception
            );
        }
    }

    private List<Map<?, ?>> findRows(Object value) {
        if (value instanceof List<?> list) {
            List<Map<?, ?>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(map);
                }
            }
            return rows;
        }

        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }

        for (String key : ITEM_KEYS) {
            Object nested = valueFor(map, key);
            if (nested instanceof List<?> || nested instanceof Map<?, ?>) {
                List<Map<?, ?>> rows = findRows(nested);
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }

        if (looksLikeMessage(map)) {
            return List.of(map);
        }

        for (Object nested : map.values()) {
            List<Map<?, ?>> rows = findRows(nested);
            if (!rows.isEmpty()) {
                return rows;
            }
        }

        return List.of();
    }

    private DisasterEvent normalizeRow(Map<?, ?> row, int index) {
        String message = firstText(row,
                "message", "msg", "contents", "content", "text",
                "MSG_CN", "NTFCTN_CN", "DSSTR_MSG", "disasterMessage"
        );
        String title = firstText(row,
                "title", "subject", "name", "EMRG_STEP_NM", "NTFCTN_TTL", "disasterTitle"
        );
        String receivedAt = firstText(row,
                "receivedAt", "issuedAt", "createDate", "createDt", "createdAt", "issuedDate",
                "CRT_DT", "NTFCTN_YMD", "NTFCTN_DT", "REG_DT", "REG_YMD", "sendDate"
        );
        String date = firstText(row, "date", "YMD", "NTFCTN_YMD", "REG_YMD");
        String time = firstText(row, "time", "TIME", "NTFCTN_TIME");
        if (receivedAt.isBlank() && (!date.isBlank() || !time.isBlank())) {
            receivedAt = (date + " " + time).trim();
        }

        return new DisasterEvent(
                fallback(firstText(row,
                        "id", "messageId", "msgId", "SN", "md101_sn", "MD101_SN",
                        "NTFCTN_SN", "SEQ", "serialNumber"
                ), "safety-data-" + index),
                receivedAt,
                fallback(firstText(row,
                        "type", "disasterType", "disasterKind", "DST_SE_NM", "DSSTR_SE_NM",
                        "NTFCTN_KIND", "disasterCategory"
                ), "UNKNOWN"),
                fallback(title, "재난문자"),
                message,
                firstText(row,
                        "region", "regionText", "location", "locationName", "area",
                        "RCPTN_RGN_NM", "LOCATION_NAME", "address"
                ),
                fallback(firstText(row,
                        "source", "sender", "organization", "sendPlatform",
                        "SNDNG_ORG_NM", "agency"
                ), "SAFETY_DATA")
        );
    }

    private boolean looksLikeMessage(Map<?, ?> map) {
        return !firstText(map,
                "message", "msg", "contents", "content", "text",
                "MSG_CN", "NTFCTN_CN", "DSSTR_MSG", "disasterMessage"
        ).isBlank();
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = valueFor(map, key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private Object valueFor(Map<?, ?> map, String expectedKey) {
        Object direct = map.get(expectedKey);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equalsIgnoreCase(expectedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String fallback(String value, String defaultValue) {
        return value.isBlank() ? defaultValue : value;
    }
}

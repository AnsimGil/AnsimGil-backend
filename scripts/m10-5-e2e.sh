#!/usr/bin/env bash

set -euo pipefail

API_BASE_URL="${ANSIMGIL_API_BASE_URL:-http://localhost:8080/api/v1}"
MODE="${1:-demo}"
E2E_PUSH_MODE="${ANSIMGIL_E2E_PUSH_MODE:-demo}"
E2E_SEND_PUSH="${ANSIMGIL_E2E_SEND_PUSH:-false}"

LOCATION_LAT="37.5665"
LOCATION_LON="126.9780"
DESTINATION_LAT="37.5705"
DESTINATION_LON="126.9920"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq가 필요합니다." >&2
  exit 1
fi

post_json() {
  local path="$1"
  local body="$2"
  curl -sS -f \
    -X POST "${API_BASE_URL}${path}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_json() {
  curl -sS -f "${API_BASE_URL}$1"
}

assert_json() {
  local label="$1"
  local payload="$2"
  local expression="$3"

  if ! jq -e "$expression" >/dev/null <<<"$payload"; then
    echo "[FAIL] ${label}" >&2
    jq . <<<"$payload" >&2 || printf '%s\n' "$payload" >&2
    exit 1
  fi
  echo "[PASS] ${label}"
}

demo_trigger_payload="$(jq -n \
  --argjson locationLat "$LOCATION_LAT" \
  --argjson locationLon "$LOCATION_LON" \
  --argjson destinationLat "$DESTINATION_LAT" \
  --argjson destinationLon "$DESTINATION_LON" \
  '{
    location: { latitude: $locationLat, longitude: $locationLon },
    destination: { latitude: $destinationLat, longitude: $destinationLon },
    limit: 5,
    dataMode: "demo",
    routeMode: "demo",
    pushMode: "demo",
    sendPush: true
  }')"

case "$MODE" in
  demo)
    echo "M10.5 Demo E2E: ${API_BASE_URL}"
    demo_trigger="$(post_json '/demo/trigger' "$demo_trigger_payload")"
    assert_json '재난→위치→날씨→FloodZone→안전경로 Demo Trigger' "$demo_trigger" \
      '.triggerStatus == "TRIGGERED" and
       .dataMode == "demo" and
       .decision.floodRelated == true and
       .decision.locationRelevant == true and
       .weather.source == "LOCAL_FIXTURE" and
       .floodZone.source == "LOCAL_DEMO" and
       .route.status == "READY" and
       .route.source == "LOCAL_FIXTURE" and
       .route.geoJson.features[0].properties.routeType == "SAFE" and
       .push.status == "SIMULATED"'
    echo "Demo E2E 완료: 외부 API 키 없이 전체 Trigger를 재현할 수 있습니다."
    ;;

  live)
    echo "M10.5 LIVE API 점검: ${API_BASE_URL}"

    disasters="$(get_json '/disasters?limit=5')"
    assert_json '행정안전부 재난문자 LIVE' "$disasters" \
      '.source == "SAFETY_DATA" and .live == true and (.events | type) == "array"'

    weather="$(get_json '/weather/short-term?latitude=37.5665&longitude=126.9780')"
    assert_json '기상청 단기예보 LIVE' "$weather" \
      '.source == "KMA_WEATHER" and .live == true and (.forecasts | length) > 0'

    flood_zones="$(get_json '/flood-zones')"
    assert_json '도시침수지도 provider 상태 확인' "$flood_zones" \
      '.geoJson.type == "FeatureCollection" and
       ((.source == "FLOOD_MAP_WMS" and .live == true) or (.geoJson.features | length) > 0)'
    if jq -e '.source == "LOCAL_DEMO" and .live == false' >/dev/null <<<"$flood_zones"; then
      echo "[INFO] 공식 도시침수지도 WMS가 설정되지 않아 Demo FloodZone을 사용합니다."
    elif jq -e '.source == "FLOOD_MAP_WMS" and .live == true' >/dev/null <<<"$flood_zones"; then
      echo "[PASS] 공식 도시침수지도 WMS LIVE provider"
    else
      echo "[INFO] 도시침수지도 provider가 응답했습니다."
    fi

    normal_route="$(post_json '/routes' "$(jq -n \
      --argjson locationLat "$LOCATION_LAT" \
      --argjson locationLon "$LOCATION_LON" \
      --argjson destinationLat "$DESTINATION_LAT" \
      --argjson destinationLon "$DESTINATION_LON" \
      '{origin: {latitude: $locationLat, longitude: $locationLon}, destination: {latitude: $destinationLat, longitude: $destinationLon}}')")"
    assert_json 'ORS 정상경로 LIVE' "$normal_route" \
      '(.type == "FeatureCollection" or .type == "Feature" or .type == "LineString")'

    safe_route="$(post_json '/routes/safe' "$(jq -n \
      --argjson locationLat "$LOCATION_LAT" \
      --argjson locationLon "$LOCATION_LON" \
      --argjson destinationLat "$DESTINATION_LAT" \
      --argjson destinationLon "$DESTINATION_LON" \
      '{origin: {latitude: $locationLat, longitude: $locationLon}, destination: {latitude: $destinationLat, longitude: $destinationLon}}')")"
    assert_json 'ORS 안전경로 LIVE' "$safe_route" \
      '(.type == "FeatureCollection" or .type == "Feature" or .type == "LineString")'

    push_token="${EXPO_PUSH_TOKEN:-}"
    if [[ "$E2E_PUSH_MODE" == "live" && "$E2E_SEND_PUSH" == "true" && -z "$push_token" ]]; then
      echo "ANSIMGIL_E2E_PUSH_MODE=live이면 EXPO_PUSH_TOKEN을 로컬 환경변수로 준비해야 합니다." >&2
      exit 1
    fi

    live_route_push_payload="$(jq -n \
      --argjson locationLat "$LOCATION_LAT" \
      --argjson locationLon "$LOCATION_LON" \
      --argjson destinationLat "$DESTINATION_LAT" \
      --argjson destinationLon "$DESTINATION_LON" \
      --arg pushMode "$E2E_PUSH_MODE" \
      --arg pushToken "$push_token" \
      --argjson sendPush "$E2E_SEND_PUSH" \
      '{
        location: { latitude: $locationLat, longitude: $locationLon },
        destination: { latitude: $destinationLat, longitude: $destinationLon },
        limit: 5,
        dataMode: "demo",
        routeMode: "ors",
        pushMode: $pushMode,
        sendPush: $sendPush,
        pushToken: $pushToken
      }')"
    live_trigger="$(post_json '/demo/trigger' "$live_route_push_payload")"
    if [[ "$E2E_PUSH_MODE" == "live" && "$E2E_SEND_PUSH" == "true" ]]; then
      assert_json 'M10 실제 ORS 경로·Expo Push LIVE' "$live_trigger" \
        '.triggerStatus == "TRIGGERED" and .route.source == "ORS" and .push.status == "SENT"'
    else
      assert_json 'M10 실제 ORS 경로 + 푸시 dry-run' "$live_trigger" \
        '.triggerStatus == "TRIGGERED" and .route.source == "ORS"'
    fi
    echo "LIVE API 점검 완료. M7 공식 도시침수지도 LIVE 여부는 위 상태를 별도로 확인했습니다."
    ;;

  *)
    echo "사용법: $0 [demo|live]" >&2
    exit 1
    ;;
esac

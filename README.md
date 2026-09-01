# 안심길 백엔드 MVP

Spring Boot 기반의 로컬 경로 API입니다. 운영 배포 없이 Android Emulator에서 실행하는 것을 기준으로 합니다.

기본 설정은 외부 API 키가 필요 없는 Demo 모드입니다. 제출·재현 환경에서는 별도 키를
입력하지 않고 바로 실행할 수 있습니다.

## 실행

Demo 모드에서는 다음처럼 실행합니다.

```bash
./gradlew bootRun
```

실제 ORS 경로를 사용할 때만 로컬 환경변수로 키를 설정합니다. 키 값은 Git에 커밋하거나
채팅·제출 압축파일에 공유하지 않습니다.

```bash
export ORS_API_KEY='발급받은 ORS Basic Key'
./gradlew bootRun
```

LIVE 장소 검색을 사용할 때는 저장해 둔 Google Places 서버 키를 백엔드 레포지토리의
`.env.local` 파일에 다음처럼 개인적으로 입력합니다. `.env.local`은 Git에서 무시되므로
제출물에 포함되지 않습니다.

```properties
GOOGLE_PLACES_API_KEY=발급받은_개인_Places_서버키
```

이제 별도로 export하지 않아도 `./gradlew bootRun`이 이 파일을 자동으로 읽습니다.
기존처럼 백엔드 실행 셸에서 `GOOGLE_PLACES_API_KEY`를 export하는 방식도 계속 지원합니다.

기본 포트는 `8080`입니다.

## API

정상 경로:

`POST /api/v1/routes`

안전 경로:

`POST /api/v1/routes/safe`

요청 예시:

```json
{
  "origin": { "latitude": 37.5665, "longitude": 126.9780 },
  "destination": { "latitude": 37.5705, "longitude": 126.9920 }
}
```

안전 경로는 FloodZone provider가 제공하는 Polygon을 `avoid_polygons`로 ORS에 전달합니다.
기본 provider는 키가 필요 없는 로컬 Demo fixture입니다.

## M7 도시침수지도 FloodZone 준비

침수 위험구역은 다음 endpoint에서 GeoJSON FeatureCollection으로 확인할 수 있습니다.

`GET /api/v1/flood-zones`

기본값은 제출·재현용 Demo 모드입니다.

```bash
FLOOD_MAP_MODE=demo
```

이 모드에서는 `src/main/resources/fixtures/flood-zones.json`을 사용하며 외부 API 키가
필요하지 않습니다. 안전 경로도 같은 Polygon을 ORS `avoid_polygons`에 사용하므로,
지도에 표시되는 위험구역과 경로 회피 대상이 분리되지 않습니다.

개인 시연 영상에서는 공식 도시침수지도 WMS를 사용하기 위해 다음 설정을
`application` 실행 환경에 로컬로 넣습니다. 현재 AnsimGil은 행정구역별 도시침수지도
서비스를 사용합니다.

```bash
FLOOD_MAP_MODE=live
FLOOD_MAP_WMS_URL='https://data.floodmap.go.kr/api/wms-service/adm-cty-wms'
FLOOD_MAP_WMS_LAYER='adm-cty-wms'
FLOOD_MAP_SERVICE_KEY='발급받은_서비스키'
FLOOD_MAP_FREQ=100
FLOOD_MAP_ADMIN_CODE=11100
FLOOD_MAP_SRS='EPSG:4326'
```

`adm-cty-wms`는 별도의 `LAYERS` 요청 파라미터가 아니라 공식 WMS 서비스 경로입니다.
`FLOOD_MAP_WMS_LAYER`는 내부 식별 호환성을 위해 유지하며 실제 요청에는 포함하지
않습니다. `STDG_SGG_CD=11100`은 공식 명세의 서울특별시 종로구 예시입니다.
다른 시군구를 시연할 때는 해당 법정동 시군구 코드로 바꿉니다. 키는 채팅, Git,
제출 압축파일에 포함하지 않습니다.

응답 예시는 다음과 같습니다.

```json
{
  "source": "LOCAL_DEMO",
  "requestedMode": "demo",
  "live": false,
  "fallbackReason": null,
  "geoJson": {
    "type": "FeatureCollection",
    "features": [
      {
        "type": "Feature",
        "properties": {
          "id": "demo-seoul-jung-flood-001",
          "name": "서울 중구 시연용 침수 위험구역"
        },
        "geometry": { "type": "Polygon", "coordinates": "..." }
      }
    ]
  }
}
```

WMS는 화면 표시용 `image/png` 지도 서비스이고, ORS 회피경로에는 Polygon GeoJSON이
필요합니다. 백엔드는 `/api/v1/flood-zones/wms-tile` 프록시로 키를 숨긴 채 공식 WMS
타일을 앱에 전달합니다. 현재 LIVE WMS는 지도 표시용이며, ORS 회피용 geometry는
기존 Demo Polygon을 유지합니다. 따라서 공식 WMS 이미지와 ORS 회피 geometry를
혼동하지 않도록 응답에서 별도로 관리합니다.

공식 요청 명세는 [홍수위험지도 API Reference](https://data.floodmap.go.kr/main/guide/api_reference?map=cityFloodMap#CityFloodMap-administrativeArea)에서
확인할 수 있습니다. 행정구역 도시침수지도 요청은 `Freq`, `STDG_SGG_CD`, `Bbox`,
`width`, `height`, `transparent`, `srs`를 사용하며, 앱의 WMS 타일 요청은 백엔드가
Google 지도 타일 범위를 공식 서비스가 이해하는 `EPSG:4326` Bbox로 변환합니다.

## M7.5 ORS Safe Routing

안전 경로 endpoint는 다음 두 입력을 사용합니다.

```text
POST /api/v1/routes/safe
  origin + destination
  + FloodZone provider의 avoid geometry
```

현재 `FLOOD_MAP_MODE=demo`에서는 로컬 서울용 Polygon이 지도 표시와 ORS
`options.avoid_polygons`에 공통으로 사용됩니다. 따라서 M7 LIVE 서비스키가 없어도
M7.5의 회피경로 흐름을 재현할 수 있습니다.

기존 ORS 키를 로컬에 설정한 뒤 실행합니다.

```bash
export ORS_API_KEY='발급받은_openrouteservice_key'
./gradlew bootRun
```

이 단계에는 새로운 API 키가 필요하지 않습니다. ORS 키가 없는 환경에서는 앱이
정상·안전 경로 fixture로 fallback하지만, 실제 회피 결과를 확인할 때는 기존 ORS 키가
필요합니다.

## M4 재난문자 연동 준비

재난문자 표준화 API는 아래처럼 조회합니다.

`GET /api/v1/disasters?limit=20`

홍수·침수 관련 문자만 조회하려면 다음 endpoint를 사용합니다.

`GET /api/v1/disasters/flood-related?limit=20`

키를 입력하지 않은 현재 상태에서는 외부 호출 없이
`src/main/resources/fixtures/disaster-messages.json`의 fixture를 반환합니다.

실제 키를 입력할 때까지는 아래 값을 변경하지 않습니다.

```bash
DISASTER_DATA_SERVICE_KEY=발급받은_안전데이터_서비스키
DISASTER_DATA_USE_LIVE=true
```

키는 로컬 환경변수나 개인 `.env` 파일에만 보관하고 Git, 채팅, 제출 소스에 포함하지 않습니다.
라이브 모드는 키 입력 후 응답 필드 확인과 정규화 검증이 끝난 다음에 켭니다.

라이브 조회는 한국시간 기준 최근 1일을 `crtDt`로 요청하고, 백엔드에서 `receivedAt` 기준 최신순으로 정렬한 뒤 `limit`만큼 반환합니다. 조회 범위는 `DISASTER_DATA_LOOKBACK_DAYS`로 조정할 수 있습니다.

M5 분류는 공식 `type`의 `홍수`·`침수`·`범람`, 메시지의 고신뢰 침수어, 또는 호우와 위험 장소·행동의 조합을 사용합니다. `호우`, `하천`, `도로`처럼 넓은 단어 하나만으로는 통과시키지 않습니다. 분류가 끝난 뒤의 문자만 후속 위치 관련성 판정으로 전달합니다.

표준 응답 예시는 다음과 같습니다.

```json
{
  "source": "LOCAL_FIXTURE",
  "live": false,
  "events": [
    {
      "id": "demo-flood-001",
      "receivedAt": "2026-08-30T09:00:00+09:00",
      "type": "FLOOD",
      "title": "호우·침수 안전 안내",
      "message": "서울 중구 일부 지역에 침수 위험이 있습니다.",
      "region": "서울특별시 중구",
      "source": "LOCAL_FIXTURE"
    }
  ]
}
```

## M6 사용자 위치 관련성 판정

M5를 통과한 홍수·침수 관련 문자만 대상으로 재난문자 지역과 사용자의 좌표를 비교합니다.
외부 지오코딩 API는 사용하지 않으므로 M6에는 새로운 API 키가 필요하지 않습니다.

```text
POST /api/v1/disasters/location-relevant
```

요청 예시:

```bash
curl -sS \
  -X POST http://localhost:8080/api/v1/disasters/location-relevant \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":37.5665,"longitude":126.9780},"limit":5}'
```

요청의 `location`은 사용자의 현재 좌표이고 `limit`은 재난문자 조회 개수입니다. `limit`을 생략하면 20건을 사용합니다.

응답은 M5 홍수 관련 이벤트별로 위치 판정 결과를 포함합니다.

```json
{
  "source": "LOCAL_FIXTURE",
  "live": false,
  "userLocation": { "latitude": 37.5665, "longitude": 126.978 },
  "hasRelevantEvent": true,
  "events": [
    {
      "event": {
        "id": "demo-flood-001",
        "receivedAt": "2026-08-30T09:00:00+09:00",
        "type": "FLOOD",
        "title": "호우·침수 안전 안내",
        "message": "서울 중구 일부 지역에 침수 위험이 있습니다.",
        "region": "서울특별시 중구",
        "source": "LOCAL_FIXTURE"
      },
      "locationRelevance": "LOCATION_RELEVANT",
      "matchMethod": "SEOUL_DISTRICT_BOUNDS",
      "matchedRegion": "서울특별시 중구"
    }
  ]
}
```

현재 판정 범위는 서울시 전체와 서울 25개 구의 재현용 근사 범위입니다. 실제 행정구역 polygon이나 주소·좌표 변환 API를 도입하는 것은 운영 단계의 확장 항목으로 남겨 두었습니다.

## M6.5 기상청 단기예보 Risk Context

M6의 위치 관련성이 확인된 뒤, 사용자 위치 주변의 단기 강수 예보를 보조 정보로 조회합니다.
예보가 없거나 기상청 API가 실패해도 재난 Trigger 자체를 차단하지 않으며, M6.5 응답만 로컬 fixture로 전환합니다.

기상청 키 없이 재현하려면 기본값 그대로 실행합니다.

```text
KMA_WEATHER_USE_LIVE=false
```

개인 환경에서 실제 API를 확인하려면 키를 채팅·Git에 공유하지 않고 로컬 환경변수로만 설정합니다.

```bash
export KMA_WEATHER_SERVICE_KEY='발급받은_기상청_서비스키'
export KMA_WEATHER_USE_LIVE=true
./gradlew bootRun
```

단기예보 조회:

```bash
curl -sS \
  'http://localhost:8080/api/v1/weather/short-term?latitude=37.5665&longitude=126.9780'
```

응답의 `grid`는 위경도를 기상청 격자 `nx`, `ny`로 변환한 값이고, `riskLevel`은 `NONE`, `POSSIBLE`, `EXPECTED` 중 하나입니다. `source`가 `KMA_WEATHER`이면 라이브 응답이고 `LOCAL_FIXTURE`이면 키가 없거나 라이브 호출에 실패해 fixture를 사용한 것입니다.

현재 M6.5는 `POP`(강수확률), `PTY`(강수형태), `PCP`(강수량), `TMP`(기온)를 정규화합니다. 기상청 단기예보는 전국 격자 기반 데이터이지만, AnsimGil의 사용자 위치 관련성은 현재 M6 정책대로 서울 범위만 판정합니다.

Android Emulator에서 실행 중인 백엔드에 접근할 때 앱의 기본 주소는 `http://10.0.2.2:8080/api/v1`입니다.

## M10 전체 Trigger E2E

M10은 재난문자부터 위치 판정, 기상 Risk Context, FloodZone, 안전경로, 대상 사용자
푸시까지를 한 번에 점검하는 로컬 전용 endpoint입니다.

```text
POST /api/v1/demo/trigger
```

기본값은 제출·재현용 `dataMode=demo`, `routeMode=demo`, `pushMode=demo`입니다. 따라서
다음 요청은 외부 API 키 없이도 실행되며, `push.status`는 실제 발송 대신 `SIMULATED`가
됩니다.

`dataMode=demo`는 백엔드 전체 설정에서 재난문자·기상청 LIVE를 켜 둔 상태에서도 M10
요청만큼은 로컬 fixture를 우선 사용하게 합니다. 개인 시연에서 실제 공공데이터를
사용하려면 요청에 `dataMode=live`를 명시합니다.

`destination`은 선택 항목입니다. 목적지를 보내면 Trigger 응답에서 안전경로까지 함께
계산하고, 목적지를 생략하면 재난·위치 판정과 FloodZone·푸시만 처리합니다. 목적지 없는
Trigger는 사용자의 목적지를 미리 알고 있지 않으므로, 알림을 확인한 뒤 앱에서 원하는
목적지를 선택하고 `/routes/safe`를 별도로 호출하는 실제 서비스 흐름에 가깝습니다.

실제 행정안전부 API 활용 시연은 `dataMode=live`를 그대로 사용합니다. 이 모드는
실행 시점의 실제 재난문자만 판정하므로 서울 중구 문자가 없으면
`LOCATION_NOT_RELEVANT`로 중단될 수 있습니다.

```bash
curl -sS \
  -X POST http://localhost:8080/api/v1/demo/trigger \
  -H 'Content-Type: application/json' \
  -d '{
    "location": { "latitude": 37.5665, "longitude": 126.9780 },
    "destination": { "latitude": 37.5705, "longitude": 126.9920 },
    "limit": 5,
    "dataMode": "demo",
    "sendPush": true
  }' | jq .
```

목적지 없이 재난 감지·푸시만 시연하려면 다음처럼 요청합니다. 이 명령은 특정 목적지에
종속되지 않으며, `route.status`는 `SKIPPED`가 됩니다.

```bash
curl -sS \
  -X POST http://localhost:8080/api/v1/demo/trigger \
  -H 'Content-Type: application/json' \
  -d '{
    "location": { "latitude": 37.5665, "longitude": 126.9780 },
    "limit": 5,
    "dataMode": "test",
    "routeMode": "ors",
    "pushMode": "live",
    "sendPush": true,
    "pushToken": "여기에_Expo_Push_Token_입력"
  }' | jq .
```

알림 URL에는 `floodAlert=true`와 재난 감지 위치만 포함되고 목적지는 포함되지 않습니다.
앱이 알림을 받은 뒤 사용자가 목적지를 입력하면, 앱이 현재 위치·새 목적지로 정상경로와
안전경로를 계산합니다.

서울 중구 Demo 재난 fixture와 일치하는 좌표를 사용하면 `dataMode`가 `demo`,
`triggerStatus`가 `TRIGGERED`가
되고, `decision.locationRelevant`가 `true`, `route.source`가 `LOCAL_FIXTURE`가 됩니다.
응답의 `route.geoJson`은 Demo FloodZone을 피하는 안전경로입니다.

개인 Android 시연 영상에서 실제 ORS 경로와 푸시를 사용하려면 키와 Expo Push Token을
로컬 환경에만 넣고 요청 단위로 모드를 바꿉니다. 토큰은 채팅·Git·제출 압축파일에
기록하지 않습니다.

```bash
export ORS_API_KEY='로컬에만_보관하는_ORS_키'
export EXPO_PUSH_TOKEN='로컬에만_보관하는_Expo_Push_Token'

curl -sS \
  -X POST http://localhost:8080/api/v1/demo/trigger \
  -H 'Content-Type: application/json' \
  -d "{
    \"location\": { \"latitude\": 37.5665, \"longitude\": 126.9780 },
    \"destination\": { \"latitude\": 37.5705, \"longitude\": 126.9920 },
    \"limit\": 5,
    \"dataMode\": \"demo\",
    \"routeMode\": \"ors\",
    \"pushMode\": \"live\",
    \"sendPush\": true,
    \"pushToken\": \"${EXPO_PUSH_TOKEN}\"
  }" | jq .
```

`routeMode=ors`는 기존 `ORS_API_KEY`를 재사용하고, `pushMode=live`는 별도 API 키 없이
Expo Push Service에 발송 요청을 전달합니다. `pushToken`은 요청 처리 후 응답에 포함되지
않습니다. 푸시 발송 실패는 경로 판정 결과를 취소하지 않고 `push.status=FAILED`로
확인할 수 있습니다.

### LIVE 위험 대상자 시연용 테스트 fixture

실제 행정안전부 LIVE 결과와 별개로, 서울 중구 사용자가 위험 대상자로 판정되는
재현 가능한 예시가 필요하면 요청의 `dataMode`만 `test`로 설정합니다. 이 모드는
`src/main/resources/fixtures/live-test-disaster-messages.json`을 사용하며 응답의
`locationRelevance.source`가 `LIVE_TEST_FIXTURE`, `live`가 `false`로 표시됩니다.
날씨와 FloodZone은 제출 가능한 로컬 fixture를 사용하고, `routeMode=ors`와
`pushMode=live`는 별도로 선택할 수 있습니다.

앱에서 `권한 확인`으로 Expo Push Token을 준비한 뒤, 토큰을 채팅·Git에 남기지 않고
아래 명령의 placeholder에 직접 입력합니다.

```bash
curl -sS \
  -X POST http://localhost:8080/api/v1/demo/trigger \
  -H 'Content-Type: application/json' \
  -d '{
    "location": { "latitude": 37.5665, "longitude": 126.9780 },
    "limit": 5,
    "dataMode": "test",
    "routeMode": "ors",
    "pushMode": "live",
    "sendPush": true,
    "pushToken": "여기에_Expo_Push_Token_입력"
  }' | jq .
```

성공 기준은 `triggerStatus=TRIGGERED`, `decision.locationRelevant=true`,
`route.status=SKIPPED`, `push.status=SENT`입니다. 실행 전 앱을 LIVE/NORMAL 상태로 두고
백그라운드로 보낸 다음 명령을 실행하면 실제 Android 푸시 알림을 확인할 수 있습니다.
알림을 누르면 `/?floodAlert=true` 흐름으로 앱이 재진입하고 FLOOD ALERT와 재현용 위험영역을
표시합니다. 이후 앱에서 원하는 목적지를 선택하고 `경로 안내 시작`을 누르면 현재 위치에서
침수구역을 회피하는 실제 안전경로를 계산합니다.

이 fixture는 행정안전부 LIVE API의 성공 응답을 의미하지 않습니다. 발표에서는
`dataMode=live`로 실제 API 활용을 보여준 뒤, `dataMode=test`로 위험 대상자·푸시·
우회경로의 재현 가능한 성공 예시를 이어서 보여주는 방식이 적합합니다.

푸시 payload의 URL은 `/?floodAlert=true`이며, `dataMode=test` fixture 푸시는
테스트 모드 정보도 함께 전달합니다. AnsimGil 앱은 이 URL로 재진입하면
FLOOD ALERT와 위험영역을 먼저 표시하고, 사용자가 목적지를 선택한 뒤 안전 경로를
계산합니다. 따라서 M10 검증 순서는 다음과 같습니다.

```text
POST /api/v1/demo/trigger
  → triggerStatus=TRIGGERED
  → push.status=SIMULATED 또는 SENT
  → Android 백그라운드에서 알림 수신
  → 알림 탭
  → FLOOD ALERT 및 위험영역 표시
  → 앱에서 원하는 목적지 선택
  → 현재 위치 기준 안전경로 계산
```

심사 제출 전에는 `ANSIMGIL_M10_DATA_MODE=demo`, `ANSIMGIL_M10_ROUTE_MODE=demo`,
`ANSIMGIL_M10_PUSH_MODE=demo`를
유지하고 `.env.local`, Expo Push Token, `google-services.json`을 제출물에서 제외합니다.

## M10.5 LIVE·DEMO E2E 점검

M10.5 점검 스크립트는 현재 로컬 서버의 LIVE/DEMO 모드를 확인합니다.
스크립트는 키 값을 출력하지 않으며, API 키는 백엔드를 시작한 로컬 환경에서만 읽습니다.

키 없이 Demo 전체 흐름을 확인합니다.

```bash
bash scripts/m10-5-e2e.sh demo
```

개인 시연 전 LIVE API를 확인할 때는 백엔드를 재시작하기 전에 기존 로컬 키와 다음
LIVE 플래그를 설정합니다. 홍수위험지도 WMS 설정이 없거나 호출에 실패하면 앱은
Demo Polygon을 유지하고 상태를 별도로 표시합니다.

```bash
export DISASTER_DATA_USE_LIVE=true
export KMA_WEATHER_USE_LIVE=true
./gradlew bootRun
```

다른 터미널에서 다음을 실행하면 행정안전부 재난문자, 기상청 단기예보, 도시침수지도
provider 상태, ORS 정상·안전경로를 순서대로 점검합니다.

```bash
bash scripts/m10-5-e2e.sh live
```

실제 푸시까지 확인하려면 토큰을 채팅에 붙여넣지 말고 로컬 환경변수로만 준비합니다.

```bash
read -s EXPO_PUSH_TOKEN
ANSIMGIL_E2E_PUSH_MODE=live \
ANSIMGIL_E2E_SEND_PUSH=true \
bash scripts/m10-5-e2e.sh live
```

LIVE 전체 점검에서 M10의 재난 이벤트는 `dataMode=demo`로 고정합니다. 현재 실제
재난문자가 서울 중구에 없더라도 ORS 실제 경로와 Expo 실제 푸시를 재현하기 위한
결정적 시나리오입니다. `dataMode=live`는 실제 서울 관련 재난문자가 존재할 때 별도로
확인할 수 있습니다.

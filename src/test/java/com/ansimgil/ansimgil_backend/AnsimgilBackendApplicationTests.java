package com.ansimgil.ansimgil_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.ansimgil.ansimgil_backend.disaster.DisasterEvent;
import com.ansimgil.ansimgil_backend.disaster.DisasterFixtureRepository;
import com.ansimgil.ansimgil_backend.disaster.LiveTestDisasterFixtureRepository;
import com.ansimgil.ansimgil_backend.disaster.DisasterNormalizer;
import com.ansimgil.ansimgil_backend.disaster.DisasterResponse;
import com.ansimgil.ansimgil_backend.disaster.DisasterService;
import com.ansimgil.ansimgil_backend.disaster.DisasterMessageClient;
import com.ansimgil.ansimgil_backend.disaster.FloodMessageClassifier;
import com.ansimgil.ansimgil_backend.disaster.FloodRelevance;
import com.ansimgil.ansimgil_backend.weather.KmaGridConverter;
import com.ansimgil.ansimgil_backend.weather.KmaGridPoint;
import com.ansimgil.ansimgil_backend.weather.WeatherForecast;
import com.ansimgil.ansimgil_backend.weather.WeatherForecastNormalizer;
import com.ansimgil.ansimgil_backend.weather.WeatherRiskEvaluator;
import com.ansimgil.ansimgil_backend.weather.WeatherRiskLevel;
import com.ansimgil.ansimgil_backend.route.RouteRequest;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnsimgilBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DisasterNormalizer disasterNormalizer;

	@Autowired
	private DisasterFixtureRepository disasterFixtureRepository;

	@Autowired
	private LiveTestDisasterFixtureRepository liveTestDisasterFixtureRepository;

	@Autowired
	private FloodMessageClassifier floodMessageClassifier;

	@Autowired
	private KmaGridConverter kmaGridConverter;

	@Autowired
	private WeatherForecastNormalizer weatherForecastNormalizer;

	@Autowired
	private WeatherRiskEvaluator weatherRiskEvaluator;

	@Test
	void contextLoads() {
	}

	@Test
	void disastersUseLocalFixtureBeforeLiveApiKeyIsConfigured() throws Exception {
		mockMvc.perform(get("/api/v1/disasters"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.live").value(false))
				.andExpect(jsonPath("$.events[0].type").value("FLOOD"));
	}

	@Test
	void normalizesOfficialSafetyDataFieldNames() {
		String rawResponse = """
				{
				  "data": [
				    {
				      "SN": "12345",
				      "CRT_DT": "2026-08-30 10:20:00",
				      "MSG_CN": "서울특별시 중구 호우로 인한 침수 위험을 안내합니다.",
				      "RCPTN_RGN_NM": "서울특별시 중구",
				      "EMRG_STEP_NM": "안전안내",
				      "DST_SE_NM": "호우",
				      "REG_YMD": "20260830"
				    }
				  ]
				}
				""";

		List<DisasterEvent> events = disasterNormalizer.normalize(rawResponse);

		org.junit.jupiter.api.Assertions.assertEquals(1, events.size());
		DisasterEvent event = events.get(0);
		org.junit.jupiter.api.Assertions.assertEquals("12345", event.id());
		org.junit.jupiter.api.Assertions.assertEquals("2026-08-30 10:20:00", event.receivedAt());
		org.junit.jupiter.api.Assertions.assertEquals(
				"서울특별시 중구 호우로 인한 침수 위험을 안내합니다.",
				event.message()
		);
		org.junit.jupiter.api.Assertions.assertEquals("서울특별시 중구", event.region());
		org.junit.jupiter.api.Assertions.assertEquals("호우", event.type());
		org.junit.jupiter.api.Assertions.assertEquals("안전안내", event.title());
	}

	@Test
	void liveEventsAreSortedNewestFirstBeforeLimitIsApplied() {
		DisasterMessageClient fakeClient = (pageNo, numOfRows, startDate) -> """
				{
				  "data": [
				    {
				      "SN": "old",
				      "CRT_DT": "2026/08/30 09:00:00",
				      "MSG_CN": "오래된 호우 안내",
				      "RCPTN_RGN_NM": "서울특별시",
				      "DST_SE_NM": "호우"
				    },
				    {
				      "SN": "new",
				      "CRT_DT": "2026/08/30 11:00:00",
				      "MSG_CN": "최신 호우 안내",
				      "RCPTN_RGN_NM": "서울특별시",
				      "DST_SE_NM": "호우"
				    }
				  ]
				}
				""";

		DisasterService service = new DisasterService(
				fakeClient,
				disasterNormalizer,
				disasterFixtureRepository,
				liveTestDisasterFixtureRepository,
				true,
				20,
				1
		);

		DisasterResponse response = service.getEvents(1);

		org.junit.jupiter.api.Assertions.assertEquals(1, response.events().size());
		org.junit.jupiter.api.Assertions.assertEquals("new", response.events().get(0).id());
	}

	@Test
	void classifiesFloodAndNonFloodMessagesWithDeterministicRules() {
		DisasterEvent floodMessage = new DisasterEvent(
				"flood-1", "2026/08/30 11:00:00", "기타", "안전안내",
				"집중호우로 하천변 침수 위험이 있습니다.", "서울특별시", "TEST"
		);
		DisasterEvent underpassControl = new DisasterEvent(
				"flood-2", "2026/08/30 11:01:00", "기타", "안전안내",
				"침수 우려로 지하차도 통제 중입니다.", "서울특별시", "TEST"
		);
		DisasterEvent heatWave = new DisasterEvent(
				"not-flood-1", "2026/08/30 11:02:00", "폭염", "안전안내",
				"폭염이 지속되니 야외활동을 자제하세요.", "서울특별시", "TEST"
		);
		DisasterEvent missingPerson = new DisasterEvent(
				"not-flood-2", "2026/08/30 11:03:00", "기타", "안전안내",
				"실종자를 찾습니다. 목격 시 신고 바랍니다.", "서울특별시", "TEST"
		);
		DisasterEvent broadWeatherWordOnly = new DisasterEvent(
				"not-flood-3", "2026/08/30 11:04:00", "기타", "안전안내",
				"호우가 예상됩니다.", "서울특별시", "TEST"
		);

		org.junit.jupiter.api.Assertions.assertEquals(
				FloodRelevance.FLOOD_RELATED,
				floodMessageClassifier.classify(floodMessage)
		);
		org.junit.jupiter.api.Assertions.assertEquals(
				FloodRelevance.FLOOD_RELATED,
				floodMessageClassifier.classify(underpassControl)
		);
		org.junit.jupiter.api.Assertions.assertEquals(
				FloodRelevance.NOT_RELATED,
				floodMessageClassifier.classify(heatWave)
		);
		org.junit.jupiter.api.Assertions.assertEquals(
				FloodRelevance.NOT_RELATED,
				floodMessageClassifier.classify(missingPerson)
		);
		org.junit.jupiter.api.Assertions.assertEquals(
				FloodRelevance.NOT_RELATED,
				floodMessageClassifier.classify(broadWeatherWordOnly)
		);
	}

	@Test
	void floodRelatedEndpointFiltersNonFloodEvents() throws Exception {
		mockMvc.perform(get("/api/v1/disasters/flood-related"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events.length()").value(1))
				.andExpect(jsonPath("$.events[0].id").value("demo-flood-001"));
	}

	@Test
	void locationRelevanceMarksFloodEventInsideMatchingSeoulDistrict() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
				"/api/v1/disasters/location-relevant"
		)
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.5665, "longitude": 126.9780 },
						  "limit": 5
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.userLocation.latitude").value(37.5665))
				.andExpect(jsonPath("$.hasRelevantEvent").value(true))
				.andExpect(jsonPath("$.events.length()").value(1))
				.andExpect(jsonPath("$.events[0].locationRelevance").value("LOCATION_RELEVANT"))
				.andExpect(jsonPath("$.events[0].matchedRegion").value("서울특별시 중구"));
	}

	@Test
	void locationRelevanceRejectsFloodEventOutsideMatchingSeoulDistrict() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
				"/api/v1/disasters/location-relevant"
		)
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.6400, "longitude": 127.1000 },
						  "limit": 5
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasRelevantEvent").value(false))
				.andExpect(jsonPath("$.events[0].locationRelevance").value("LOCATION_NOT_RELEVANT"))
				.andExpect(jsonPath("$.events[0].matchMethod").value("REGION_NOT_MATCHED"));
	}

	@Test
	void liveTestFixtureTriggersSeoulTargetScenarioWithoutCallingLiveDisasterApi() throws Exception {
		mockMvc.perform(post("/api/v1/demo/trigger")
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.5665, "longitude": 126.9780 },
						  "destination": { "latitude": 37.5705, "longitude": 126.9920 },
						  "dataMode": "test",
						  "routeMode": "demo",
						  "pushMode": "demo",
						  "sendPush": false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.triggerStatus").value("TRIGGERED"))
				.andExpect(jsonPath("$.dataMode").value("test"))
				.andExpect(jsonPath("$.decision.floodRelated").value(true))
				.andExpect(jsonPath("$.decision.locationRelevant").value(true))
				.andExpect(jsonPath("$.decision.eventId").value("live-test-seoul-jung-flood-001"))
				.andExpect(jsonPath("$.locationRelevance.source").value("LIVE_TEST_FIXTURE"))
				.andExpect(jsonPath("$.locationRelevance.live").value(false))
				.andExpect(jsonPath("$.weather.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.floodZone.source").value("LOCAL_DEMO"))
				.andExpect(jsonPath("$.route.status").value("READY"))
				.andExpect(jsonPath("$.push.status").value("SKIPPED"));
	}

	@Test
	void triggerCanEvaluateLocationAndPushWithoutKnowingDestination() throws Exception {
		mockMvc.perform(post("/api/v1/demo/trigger")
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.5665, "longitude": 126.9780 },
						  "dataMode": "test",
						  "routeMode": "ors",
						  "pushMode": "demo",
						  "sendPush": true
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.triggerStatus").value("TRIGGERED"))
				.andExpect(jsonPath("$.decision.locationRelevant").value(true))
				.andExpect(jsonPath("$.route.status").value("SKIPPED"))
				.andExpect(jsonPath("$.route.error").value(
						"목적지가 지정되지 않아 재난 Trigger만 처리했습니다. 앱에서 목적지를 선택하면 경로를 계산합니다."
				))
				.andExpect(jsonPath("$.push.status").value("SIMULATED"))
				.andExpect(jsonPath("$.push.notificationUrl")
						.value("/?floodAlert=true&originLatitude=37.5665&originLongitude=126.978"));
	}

	@Test
	void locationRelevanceValidatesNestedCoordinate() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
				"/api/v1/disasters/location-relevant"
		)
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 91.0, "longitude": 126.9780 }
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void convertsSeoulCoordinateToKmaGrid() {
		KmaGridPoint grid = kmaGridConverter.convert(
				new RouteRequest.Coordinate(37.5665, 126.9780)
		);

		org.junit.jupiter.api.Assertions.assertEquals(60, grid.nx());
		org.junit.jupiter.api.Assertions.assertEquals(127, grid.ny());
	}

	@Test
	void normalizesKmaForecastItemsAndCalculatesRainRisk() {
		List<WeatherForecast> forecasts = weatherForecastNormalizer.normalize("""
				{
				  "response": {
				    "body": {
				      "items": {
				        "item": [
				          {"category":"POP","fcstDate":"20260830","fcstTime":"1200","fcstValue":"70"},
				          {"category":"PTY","fcstDate":"20260830","fcstTime":"1200","fcstValue":"1"},
				          {"category":"PCP","fcstDate":"20260830","fcstTime":"1200","fcstValue":"5~10mm"},
				          {"category":"TMP","fcstDate":"20260830","fcstTime":"1200","fcstValue":"26"}
				        ]
				      }
				    }
				  }
				}
				""");

		org.junit.jupiter.api.Assertions.assertEquals(1, forecasts.size());
		WeatherForecast forecast = forecasts.get(0);
		org.junit.jupiter.api.Assertions.assertEquals(70, forecast.precipitationProbability());
		org.junit.jupiter.api.Assertions.assertEquals("비", forecast.precipitationType());
		org.junit.jupiter.api.Assertions.assertEquals("5~10mm", forecast.precipitationAmount());
		org.junit.jupiter.api.Assertions.assertEquals(
				WeatherRiskLevel.EXPECTED,
				weatherRiskEvaluator.evaluate(forecasts)
		);
	}

	@Test
	void shortTermWeatherEndpointUsesLocalFixtureByDefault() throws Exception {
		mockMvc.perform(get("/api/v1/weather/short-term")
				.param("latitude", "37.5665")
				.param("longitude", "126.9780"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.live").value(false))
				.andExpect(jsonPath("$.grid.nx").value(60))
				.andExpect(jsonPath("$.grid.ny").value(127))
				.andExpect(jsonPath("$.riskLevel").value("EXPECTED"))
				.andExpect(jsonPath("$.fallbackReason").value("LIVE_DISABLED"))
				.andExpect(jsonPath("$.forecasts[0].precipitationType").value("비"));
	}

	@Test
	void shortTermWeatherEndpointRejectsInvalidCoordinate() throws Exception {
		mockMvc.perform(get("/api/v1/weather/short-term")
				.param("latitude", "91")
				.param("longitude", "126.9780"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void floodZoneEndpointUsesLocalFixtureByDefault() throws Exception {
		mockMvc.perform(get("/api/v1/flood-zones"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("LOCAL_DEMO"))
				.andExpect(jsonPath("$.requestedMode").value("demo"))
				.andExpect(jsonPath("$.live").value(false))
				.andExpect(jsonPath("$.fallbackReason").isEmpty())
				.andExpect(jsonPath("$.geoJson.type").value("FeatureCollection"))
				.andExpect(jsonPath("$.geoJson.features[0].geometry.type").value("Polygon"));
	}

	@Test
	void floodMapWmsTileDoesNotCallExternalServiceInDemoMode() throws Exception {
		mockMvc.perform(get("/api/v1/flood-zones/wms-tile")
				.param("minX", "14100000")
				.param("maxX", "14110000")
				.param("minY", "4500000")
				.param("maxY", "4510000"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void m10DemoTriggerRunsTheFullKeylessDecisionAndSafeRouteFlow() throws Exception {
		mockMvc.perform(post("/api/v1/demo/trigger")
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.5665, "longitude": 126.9780 },
						  "destination": { "latitude": 37.5705, "longitude": 126.9920 },
						  "limit": 5,
						  "sendPush": false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.triggerStatus").value("TRIGGERED"))
				.andExpect(jsonPath("$.decision.floodRelated").value(true))
				.andExpect(jsonPath("$.decision.locationRelevant").value(true))
				.andExpect(jsonPath("$.decision.eventId").value("demo-flood-001"))
				.andExpect(jsonPath("$.weather.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.floodZone.source").value("LOCAL_DEMO"))
				.andExpect(jsonPath("$.route.status").value("READY"))
				.andExpect(jsonPath("$.route.mode").value("demo"))
				.andExpect(jsonPath("$.route.source").value("LOCAL_FIXTURE"))
				.andExpect(jsonPath("$.route.geoJson.features[0].properties.routeType").value("SAFE"))
				.andExpect(jsonPath("$.route.geoJson.features[0].geometry.coordinates[1][1]").value(37.5635))
				.andExpect(jsonPath("$.route.geoJson.features[0].geometry.coordinates[2][0]").value(126.994))
				.andExpect(jsonPath("$.push.status").value("SKIPPED"));
	}

	@Test
	void m10FixtureUsesRequestedDestinationInRouteAndNotificationLink() throws Exception {
		mockMvc.perform(post("/api/v1/demo/trigger")
				.contentType("application/json")
				.content("""
						{
						  "location": { "latitude": 37.5665, "longitude": 126.9780 },
						  "destination": { "latitude": 37.5710, "longitude": 126.9900 },
						  "dataMode": "test",
						  "routeMode": "demo",
						  "pushMode": "demo",
						  "sendPush": true
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.route.geoJson.features[0].geometry.coordinates[4][0]").value(126.99))
				.andExpect(jsonPath("$.route.geoJson.features[0].geometry.coordinates[4][1]").value(37.571))
				.andExpect(jsonPath("$.push.status").value("SIMULATED"))
				.andExpect(jsonPath("$.push.notificationUrl")
						.value("/?floodAlert=true&originLatitude=37.5665&originLongitude=126.978&destinationLatitude=37.571&destinationLongitude=126.99"));
	}

}

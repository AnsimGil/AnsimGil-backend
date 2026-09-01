package com.ansimgil.ansimgil_backend.weather;

import com.ansimgil.ansimgil_backend.route.RouteRequest;
import org.springframework.stereotype.Component;

/**
 * 기상청 단기예보의 위경도 좌표를 Lambert Conformal Conic 격자로 변환합니다.
 * 기상청 단기예보의 표준 변환식이며, 별도 지오코딩 API를 호출하지 않습니다.
 */
@Component
public class KmaGridConverter {
    private static final double RE = 6371.00877;
    private static final double GRID = 5.0;
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;
    private static final double XO = 43.0;
    private static final double YO = 136.0;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

    public KmaGridPoint convert(RouteRequest.Coordinate coordinate) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGREES_TO_RADIANS;
        double slat2 = SLAT2 * DEGREES_TO_RADIANS;
        double olon = OLON * DEGREES_TO_RADIANS;
        double olat = OLAT * DEGREES_TO_RADIANS;

        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(
                        Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                                / Math.tan(Math.PI * 0.25 + slat1 * 0.5)
                );
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn)
                * Math.cos(slat1)
                / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);

        double ra = re * sf
                / Math.pow(
                        Math.tan(Math.PI * 0.25 + coordinate.latitude() * DEGREES_TO_RADIANS * 0.5),
                        sn
                );
        double theta = coordinate.longitude() * DEGREES_TO_RADIANS - olon;
        theta = normalizeLongitude(theta) * sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new KmaGridPoint(nx, ny);
    }

    private double normalizeLongitude(double longitude) {
        if (longitude > Math.PI) return longitude - (2 * Math.PI);
        if (longitude < -Math.PI) return longitude + (2 * Math.PI);
        return longitude;
    }
}

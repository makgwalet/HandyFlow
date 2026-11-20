package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ForecastRules.Direction;
import za.co.handyflow.platform.legalcompliance.application.internal.ForecastRules.Fit;
import za.co.handyflow.platform.legalcompliance.application.internal.ForecastRules.Point;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastRulesTest {

    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);

    private static List<Point> monthly(int... pct) {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < pct.length; i++) out.add(new Point(D0.plusDays(30L * i), pct[i]));
        return out;
    }

    @Test @DisplayName("Too few snapshots, or too short a span, gives no line at all")
    void insufficient() {
        assertEquals(Direction.INSUFFICIENT_DATA, ForecastRules.fit(monthly(50, 60, 70)).direction());
        assertNull(ForecastRules.fit(monthly(50, 60, 70)).projected90());
        List<Point> tight = new ArrayList<>();
        for (int i = 0; i < 5; i++) tight.add(new Point(D0.plusDays(i * 5L), 50 + i));
        assertEquals(Direction.INSUFFICIENT_DATA, ForecastRules.fit(tight).direction());
        assertEquals(Direction.INSUFFICIENT_DATA, ForecastRules.fit(List.of()).direction());
    }

    @Test @DisplayName("A clean upward series is improving with a sensible projection")
    void improving() {
        Fit f = ForecastRules.fit(monthly(50, 55, 60, 65, 70));
        assertEquals(Direction.IMPROVING, f.direction());
        assertEquals(5.0, f.slopePer30Days(), 0.01);
        assertEquals(1.0, f.r2(), 0.001);
        assertEquals(Integer.valueOf(75), f.projected30());
        assertEquals(Integer.valueOf(85), f.projected90());
    }

    @Test @DisplayName("A downward series is declining, and projections are clamped to 0..100")
    void decliningAndClamped() {
        Fit f = ForecastRules.fit(monthly(40, 30, 20, 10, 5));
        assertEquals(Direction.DECLINING, f.direction());
        assertEquals(Integer.valueOf(0), f.projected90());
        Fit up = ForecastRules.fit(monthly(80, 88, 95, 100, 100));
        assertTrue(up.projected90() <= 100);
    }

    @Test @DisplayName("A flat series is steady; tiny drift is also steady")
    void steady() {
        assertEquals(Direction.STEADY, ForecastRules.fit(monthly(80, 80, 80, 80)).direction());
        assertEquals(Integer.valueOf(80), ForecastRules.fit(monthly(80, 80, 80, 80)).projected90());
        assertEquals(Direction.STEADY, ForecastRules.fit(monthly(80, 80, 81, 81, 81)).direction());
    }

    @Test @DisplayName("An erratic series has an unclear direction and shows no projection")
    void unclear() {
        Fit f = ForecastRules.fit(monthly(40, 90, 35, 95, 45, 85));
        assertEquals(Direction.UNCLEAR, f.direction());
        assertNull(f.projected30());
        assertNull(f.projected90());
        assertTrue(f.r2() < ForecastRules.MIN_R2);
    }

    @Test @DisplayName("Input order does not matter")
    void orderIndependent() {
        List<Point> pts = monthly(50, 55, 60, 65, 70);
        Collections.reverse(pts);
        assertEquals(Direction.IMPROVING, ForecastRules.fit(pts).direction());
        assertEquals(Integer.valueOf(75), ForecastRules.fit(pts).projected30());
    }
}

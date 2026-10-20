package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoMathTest {
    // Roughly Johannesburg CBD to Pretoria CBD is about 54 km in a straight line.
    @Test void distanceBetweenTwoCitiesIsAboutRight() {
        double m = GeoMath.haversineMeters(-26.2041, 28.0473, -25.7479, 28.2293);
        assertTrue(m > 50_000 && m < 58_000, "was " + m);
    }

    @Test void theSamePointIsZeroAndDistanceIsSymmetric() {
        assertEquals(0.0, GeoMath.haversineMeters(-26.0, 28.0, -26.0, 28.0), 0.0001);
        assertEquals(GeoMath.haversineMeters(-26.0, 28.0, -25.5, 28.5), GeoMath.haversineMeters(-25.5, 28.5, -26.0, 28.0), 0.0001);
    }

    @Test void oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertEquals(111_195, GeoMath.haversineMeters(0, 0, 1, 0), 300);
    }

    @Test void pointsAcrossTheDateLineAreStillClose() {
        assertTrue(GeoMath.haversineMeters(0, 179.9, 0, -179.9) < 25_000);
    }

    static final double[][] SQUARE = {{-26.0, 28.0}, {-26.0, 28.1}, {-26.1, 28.1}, {-26.1, 28.0}};

    @Test void aPointInsideAPolygonIsInsideAndOneOutsideIsNot() {
        assertTrue(GeoMath.inPolygon(-26.05, 28.05, SQUARE));
        assertFalse(GeoMath.inPolygon(-26.2, 28.05, SQUARE));
        assertFalse(GeoMath.inPolygon(-26.05, 28.2, SQUARE));
        assertFalse(GeoMath.inPolygon(-25.9, 28.05, SQUARE));
    }

    @Test void aConcavePolygonExcludesItsNotch() {
        double[][] u = {{0, 0}, {0, 3}, {3, 3}, {3, 2}, {1, 2}, {1, 1}, {3, 1}, {3, 0}};
        assertTrue(GeoMath.inPolygon(0.5, 1.5, u));
        assertFalse(GeoMath.inPolygon(2, 1.5, u)); // inside the notch
    }

    @Test void polygonsRoundTripThroughJson() {
        String json = GeoMath.toPolygonJson(SQUARE);
        double[][] back = GeoMath.parsePolygon(json);
        assertEquals(4, back.length);
        assertEquals(-26.1, back[2][0], 1e-9);
        assertEquals(28.1, back[2][1], 1e-9);
        assertNull(GeoMath.parsePolygon(null));
        assertThrows(IllegalStateException.class, () -> GeoMath.parsePolygon("not json"));
    }
}

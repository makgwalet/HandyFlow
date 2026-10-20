package za.co.handyflow.platform.fleet.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import za.co.handyflow.platform.fleet.domain.model.Geofence;
import za.co.handyflow.platform.fleet.domain.model.GeofenceShape;

/**
 * Small geometry helpers for geofencing and track distance. Pure functions, no Spring.
 * Polygon tests treat latitude/longitude as a flat plane, which is accurate for the zone sizes a fleet
 * draws (depots, towns); it would not be for a polygon spanning a pole or the antimeridian.
 */
final class GeoMath {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final ObjectMapper JSON = new ObjectMapper();

    private GeoMath() {}

    /** Great-circle distance in metres. */
    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Ray casting. {@code polygon} is [[lat, lng], ...]; the boundary itself counts as inside or outside arbitrarily. */
    static boolean inPolygon(double lat, double lng, double[][] polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double yi = polygon[i][0], xi = polygon[i][1];
            double yj = polygon[j][0], xj = polygon[j][1];
            boolean crosses = (yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi;
            if (crosses) inside = !inside;
        }
        return inside;
    }

    /** Whether the point is inside the geofence. {@code polygon} must be the parsed polygon for POLYGON fences (else ignored). */
    static boolean contains(Geofence g, double[][] polygon, double lat, double lng) {
        if (g.getShape() == GeofenceShape.CIRCLE) {
            return haversineMeters(lat, lng, g.getCenterLatitude(), g.getCenterLongitude()) <= g.getRadiusMeters();
        }
        return polygon != null && polygon.length >= 3 && inPolygon(lat, lng, polygon);
    }

    static double[][] parsePolygon(String json) {
        if (json == null) return null;
        try {
            return JSON.readValue(json, double[][].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored geofence polygon is not valid JSON", e);
        }
    }

    static String toPolygonJson(double[][] points) {
        try {
            return JSON.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise geofence polygon", e);
        }
    }
}
package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A zone on the map: a circle (centre + radius) or a polygon (three or more [lat, lng] points,
 * stored as JSON text). It can raise events when a vehicle enters or leaves, and can impose a lower
 * speed limit inside it (a depot yard, a town) — where several apply the lowest limit wins.
 * The geometry maths lives in GeoMath; this entity validates and stores.
 */
@Entity
@Table(name = "fleet_geofences")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Geofence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GeofenceShape shape;

    @Column(name = "center_latitude")
    private Double centerLatitude;

    @Column(name = "center_longitude")
    private Double centerLongitude;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    @Column(name = "polygon_json", columnDefinition = "TEXT")
    private String polygonJson;

    @Column(name = "alert_on_enter", nullable = false)
    private boolean alertOnEnter;

    @Column(name = "alert_on_exit", nullable = false)
    private boolean alertOnExit;

    @Column(name = "speed_limit_kph")
    private Integer speedLimitKph;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static Geofence create(TenantId tenantId) {
        Geofence g = new Geofence();
        g.tenantId = tenantId;
        g.createdAt = Instant.now();
        g.updatedAt = Instant.now();
        return g;
    }

    /** Full replace. {@code polygonJson} is already validated and serialised by the caller. */
    public void apply(String name, GeofenceShape shape, Double centerLatitude, Double centerLongitude,
                      Integer radiusMeters, String polygonJson, boolean alertOnEnter, boolean alertOnExit,
                      Integer speedLimitKph, boolean active) {
        if (shape == GeofenceShape.CIRCLE) {
            if (centerLatitude == null || centerLongitude == null || radiusMeters == null) {
                throw new IllegalArgumentException("A circle needs a centre (latitude, longitude) and a radius");
            }
            if (radiusMeters < 10 || radiusMeters > 100_000) {
                throw new IllegalArgumentException("Radius must be between 10 m and 100 km");
            }
            this.polygonJson = null;
        } else {
            if (polygonJson == null) throw new IllegalArgumentException("A polygon needs at least three points");
            this.centerLatitude = null;
            this.centerLongitude = null;
            this.radiusMeters = null;
        }
        if (speedLimitKph != null && (speedLimitKph < 5 || speedLimitKph > 200)) {
            throw new IllegalArgumentException("Speed limit must be between 5 and 200 km/h");
        }
        this.name = name;
        this.shape = shape;
        if (shape == GeofenceShape.CIRCLE) {
            this.centerLatitude = centerLatitude;
            this.centerLongitude = centerLongitude;
            this.radiusMeters = radiusMeters;
        } else {
            this.polygonJson = polygonJson;
        }
        this.alertOnEnter = alertOnEnter;
        this.alertOnExit = alertOnExit;
        this.speedLimitKph = speedLimitKph;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Whether a company leaves its administrators off the routine notification for one alert type. */
@Entity
@Table(name = "fuel_alert_topic_settings")
@IdClass(FuelAlertTopicSetting.Key.class)
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelAlertTopicSetting {

    public static class Key implements java.io.Serializable {
        public UUID tenantId;
        public String topic;
        public Key() {}
        @Override public boolean equals(Object o) { return o instanceof Key k && java.util.Objects.equals(tenantId, k.tenantId) && java.util.Objects.equals(topic, k.topic); }
        @Override public int hashCode() { return java.util.Objects.hash(tenantId, topic); }
    }

    @Id @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id @Column(nullable = false, updatable = false, length = 30)
    private String topic;

    @Column(name = "exclude_admins", nullable = false)
    private boolean excludeAdmins;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static FuelAlertTopicSetting of(TenantId tenant, String topic, boolean excludeAdmins, UUID by) {
        FuelAlertTopicSetting s = new FuelAlertTopicSetting();
        s.tenantId = tenant.getValue(); s.topic = topic; s.excludeAdmins = excludeAdmins; s.updatedBy = by;
        return s;
    }

    public void set(boolean excludeAdmins, UUID by) { this.excludeAdmins = excludeAdmins; this.updatedBy = by; this.updatedAt = Instant.now(); }
}

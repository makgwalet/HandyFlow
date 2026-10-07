// security/domain/model/GuardReadinessSettings.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Which screenings and guard-file documents a tenant requires for deployment readiness. One row per tenant. */
@Entity
@Table(name = "security_readiness_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardReadinessSettings {

    @Id
    private java.util.UUID id = java.util.UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "required_screening", nullable = false, length = 500) private String requiredScreening = "";
    @Column(name = "required_documents", nullable = false, length = 500) private String requiredDocuments = "";
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "updated_by_name", length = 200) private String updatedByName;

    public static GuardReadinessSettings forTenant(TenantId tenantId) {
        GuardReadinessSettings s = new GuardReadinessSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void apply(GuardReadinessCalculator.Requirements r, String byName) {
        requiredScreening = String.join(",", r.screening());
        requiredDocuments = String.join(",", r.documents());
        updatedAt = Instant.now();
        updatedByName = byName;
    }

    public GuardReadinessCalculator.Requirements toRequirements() {
        return new GuardReadinessCalculator.Requirements(split(requiredScreening), split(requiredDocuments));
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What the organisation is and does. Used only to produce applicability SUGGESTIONS. */
@Entity
@Table(name = "legalcompliance_org_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationProfile extends AggregateRoot<OrganisationProfile> {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> industries = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> activities = new ArrayList<>();

    @Column(name = "employee_count") private Integer employeeCount;
    @Column(name = "entity_type", length = 100) private String entityType;
    @Column(name = "updated_by") private UUID updatedBy;

    public static OrganisationProfile create(TenantId tenantId) {
        OrganisationProfile p = new OrganisationProfile();
        p.initTenantId(tenantId);
        return p;
    }

    public void update(List<String> industries, List<String> activities, Integer employeeCount, String entityType, UUID by) {
        this.industries = normalise(industries);
        this.activities = normalise(activities);
        this.employeeCount = employeeCount;
        this.entityType = entityType;
        this.updatedBy = by;
    }

    private static List<String> normalise(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) if (s != null && !s.isBlank() && !out.contains(s.trim().toUpperCase())) out.add(s.trim().toUpperCase());
        return out;
    }
}

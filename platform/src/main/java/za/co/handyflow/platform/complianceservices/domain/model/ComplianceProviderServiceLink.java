package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One service a provider offers, by service code. */
@Entity(name = "ComplianceProviderServiceLink")
@Table(name = "compliance_provider_services")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceProviderServiceLink {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "service_code", nullable = false)
    private String serviceCode;
    @Version
    private Long version;

    public static ComplianceProviderServiceLink of(TenantId tenantId, UUID providerId, String serviceCode) {
        ComplianceProviderServiceLink l = new ComplianceProviderServiceLink();
        l.tenantId = tenantId;
        l.providerId = providerId;
        l.serviceCode = serviceCode;
        return l;
    }
}

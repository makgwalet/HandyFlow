package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A law, regulation, standard, code or licence condition.
 * tenantId == null  -> platform-curated catalogue row, read-only to tenants (they ADOPT a copy).
 * tenantId != null  -> the tenant's own row.
 */
@Entity
@Table(name = "legalcompliance_regulatory_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryInstrument {

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(nullable = false, length = 100) private String identifier;
    @Column(nullable = false, length = 255) private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 30)
    private InstrumentType instrumentType;

    @Column(nullable = false, length = 10) private String jurisdiction = "ZA";

    @Enumerated(EnumType.STRING)
    @Column(name = "jurisdiction_level", nullable = false, length = 20)
    private JurisdictionLevel jurisdictionLevel;

    @Column(length = 255) private String authority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ObligationCategory topic;

    @Column(name = "source_url", length = 500) private String sourceUrl;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "source_instrument_id") private UUID sourceInstrumentId;

    @Column(nullable = false) private boolean reviewed;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_by_name", length = 255) private String reviewedByName;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @jakarta.persistence.Version private Long version;

    public static RegulatoryInstrument create(UUID tenantId, String identifier, String title, InstrumentType type,
                                              JurisdictionLevel level, String authority, ObligationCategory topic,
                                              String sourceUrl, String description, UUID sourceInstrumentId, UUID createdBy) {
        RegulatoryInstrument i = new RegulatoryInstrument();
        i.id = UUID.randomUUID();
        i.tenantId = tenantId;
        i.identifier = identifier;
        i.title = title;
        i.instrumentType = type;
        i.jurisdictionLevel = level;
        i.authority = authority;
        i.topic = topic;
        i.sourceUrl = sourceUrl;
        i.description = description;
        i.sourceInstrumentId = sourceInstrumentId;
        i.createdBy = createdBy;
        i.createdAt = Instant.now();
        i.updatedAt = i.createdAt;
        return i;
    }

    public void update(String identifier, String title, InstrumentType type, JurisdictionLevel level, String authority,
                       ObligationCategory topic, String sourceUrl, String description) {
        this.identifier = identifier;
        this.title = title;
        this.instrumentType = type;
        this.jurisdictionLevel = level;
        this.authority = authority;
        this.topic = topic;
        this.sourceUrl = sourceUrl;
        this.description = description;
        this.reviewed = false;            // any content change needs a fresh review
        this.reviewedBy = null;
        this.reviewedByName = null;
        this.reviewedAt = null;
        touch();
    }

    public void markReviewed(UUID by, String byName) {
        this.reviewed = true;
        this.reviewedBy = by;
        this.reviewedByName = byName;
        this.reviewedAt = Instant.now();
        touch();
    }

    public boolean isPlatform() { return tenantId == null; }

    private void touch() { this.updatedAt = Instant.now(); }
}

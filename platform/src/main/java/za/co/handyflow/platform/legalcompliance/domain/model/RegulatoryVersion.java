package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One published (or draft) edition of an instrument. Once published, its requirements never change. */
@Entity
@Table(name = "legalcompliance_regulatory_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryVersion {

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "instrument_id", nullable = false) private UUID instrumentId;
    @Column(name = "version_label", nullable = false, length = 100) private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "amended_date") private LocalDate amendedDate;
    @Column(name = "repealed_date") private LocalDate repealedDate;
    @Column(name = "source_url", length = 500) private String sourceUrl;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(name = "published_by_name", length = 255) private String publishedByName;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @jakarta.persistence.Version private Long version;

    public static RegulatoryVersion createDraft(UUID tenantId, UUID instrumentId, String label, LocalDate effectiveDate,
                                                LocalDate amendedDate, String sourceUrl, String summary, UUID createdBy) {
        RegulatoryVersion v = new RegulatoryVersion();
        v.id = UUID.randomUUID();
        v.tenantId = tenantId;
        v.instrumentId = instrumentId;
        v.versionLabel = label;
        v.effectiveDate = effectiveDate;
        v.amendedDate = amendedDate;
        v.sourceUrl = sourceUrl;
        v.summary = summary;
        v.createdBy = createdBy;
        v.createdAt = Instant.now();
        v.updatedAt = v.createdAt;
        return v;
    }

    public void editDraft(String label, LocalDate effectiveDate, LocalDate amendedDate, String sourceUrl, String summary) {
        requireStatus(VersionStatus.DRAFT, "edited");
        this.versionLabel = label;
        this.effectiveDate = effectiveDate;
        this.amendedDate = amendedDate;
        this.sourceUrl = sourceUrl;
        this.summary = summary;
        touch();
    }

    public void publish(UUID by, String byName) {
        requireStatus(VersionStatus.DRAFT, "published");
        if (effectiveDate == null) throw new IllegalStateException("An effective date is required before publishing");
        status = VersionStatus.CURRENT;
        publishedAt = Instant.now();
        publishedBy = by;
        publishedByName = byName;
        touch();
    }

    public void supersede() {
        requireStatus(VersionStatus.CURRENT, "superseded");
        status = VersionStatus.SUPERSEDED;
        touch();
    }

    public void repeal(LocalDate date) {
        if (status != VersionStatus.CURRENT && status != VersionStatus.SUPERSEDED)
            throw new IllegalStateException("Only a published version can be repealed — current: " + status);
        status = VersionStatus.REPEALED;
        repealedDate = date;
        touch();
    }

    public boolean isDraft() { return status == VersionStatus.DRAFT; }

    private void requireStatus(VersionStatus required, String action) {
        if (status != required)
            throw new IllegalStateException("Only a " + required + " version can be " + action + " — current: " + status);
    }

    private void touch() { this.updatedAt = Instant.now(); }
}

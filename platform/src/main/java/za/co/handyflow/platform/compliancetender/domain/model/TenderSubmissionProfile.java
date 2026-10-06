package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** A reusable, named set of submission rules (ADR-005 decision 7). Limits left null are "not stated". */
@Entity
@Table(name = "tender_submission_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderSubmissionProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "allowed_extensions")
    private String allowedExtensions;

    @Column(name = "max_file_bytes")
    private Long maxFileBytes;

    @Column(name = "max_total_bytes")
    private Long maxTotalBytes;

    @Column(name = "max_file_count")
    private Integer maxFileCount;

    @Column(name = "zip_allowed")
    private Boolean zipAllowed;

    @Column(name = "max_file_name_length")
    private Integer maxFileNameLength;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static TenderSubmissionProfile create(TenantId tenantId, String name, Set<String> extensions, Long maxFileBytes, Long maxTotalBytes,
                                                 Integer maxFileCount, Boolean zipAllowed, Integer maxFileNameLength, UUID createdBy) {
        TenderSubmissionProfile p = new TenderSubmissionProfile();
        p.tenantId = tenantId;
        p.createdAt = Instant.now();
        p.createdBy = createdBy;
        p.apply(name, extensions, maxFileBytes, maxTotalBytes, maxFileCount, zipAllowed, maxFileNameLength, createdBy);
        return p;
    }

    public void apply(String name, Set<String> extensions, Long maxFileBytes, Long maxTotalBytes, Integer maxFileCount,
                      Boolean zipAllowed, Integer maxFileNameLength, UUID updatedBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A submission profile needs a name.");
        requirePositive(maxFileBytes == null ? null : maxFileBytes.longValue(), "Maximum file size");
        requirePositive(maxTotalBytes == null ? null : maxTotalBytes.longValue(), "Maximum total size");
        requirePositive(maxFileCount == null ? null : maxFileCount.longValue(), "Maximum file count");
        requirePositive(maxFileNameLength == null ? null : maxFileNameLength.longValue(), "Maximum file name length");
        this.name = name.trim();
        this.allowedExtensions = joinExtensions(extensions);
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxFileCount = maxFileCount;
        this.zipAllowed = zipAllowed;
        this.maxFileNameLength = maxFileNameLength;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public Set<String> extensionSet() {
        if (allowedExtensions == null || allowedExtensions.isBlank()) return null;
        return new TreeSet<>(Arrays.asList(allowedExtensions.split(",")));
    }

    /** Lower-case, no dots, no blanks, no duplicates; an empty or null list means "not restricted". */
    static String joinExtensions(Set<String> extensions) {
        if (extensions == null) return null;
        TreeSet<String> clean = new TreeSet<>();
        for (String e : extensions) {
            if (e == null) continue;
            String x = e.trim().toLowerCase(Locale.ROOT);
            if (x.startsWith(".")) x = x.substring(1);
            if (!x.isEmpty()) clean.add(x);
        }
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static void requirePositive(Long value, String what) {
        if (value != null && value <= 0) throw new IllegalArgumentException(what + " must be more than zero when it is set.");
    }
}

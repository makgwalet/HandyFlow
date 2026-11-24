package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Where one fact on a client's profile came from, and whether a second person has confirmed it.
 * Changing the fact starts a new record, so an old confirmation never covers a new value.
 */
@Entity
@Table(name = "compliance_client_field_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClientFieldSource {

    public static final int MIN_EXCEPTION_REASON = 15;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(nullable = false)
    private String source;

    private String method;
    private String reference;

    @Column(name = "source_date")
    private LocalDate sourceDate;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_exception_reason", columnDefinition = "TEXT")
    private String verificationExceptionReason;

    @Version
    private Long version;

    public static ComplianceClientFieldSource record(TenantId tenantId, UUID clientId, String fieldName, String source,
                                                     String method, String reference, LocalDate sourceDate, UUID recordedBy) {
        if (!ClientProfileRules.TRACKED_FIELDS.contains(fieldName)) throw new IllegalArgumentException("Unknown profile field: " + fieldName);
        String src = ClientProfileRules.requireOneOf("source", source, ClientProfileRules.SOURCES);
        if (src == null) throw new IllegalArgumentException("source is required");
        ComplianceClientFieldSource s = new ComplianceClientFieldSource();
        s.tenantId = tenantId;
        s.clientId = clientId;
        s.fieldName = fieldName;
        s.restate(src, method, reference, sourceDate, recordedBy);
        return s;
    }

    /** Replaces the source details and clears any earlier confirmation. */
    public void restate(String source, String method, String reference, LocalDate sourceDate, UUID recordedBy) {
        String src = ClientProfileRules.requireOneOf("source", source, ClientProfileRules.SOURCES);
        if (src == null) throw new IllegalArgumentException("source is required");
        this.source = src;
        this.method = ClientProfileRules.clean(method, 200);
        this.reference = ClientProfileRules.clean(reference, 200);
        this.sourceDate = sourceDate;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
        this.verifiedBy = null;
        this.verifiedAt = null;
        this.verificationExceptionReason = null;
    }

    public boolean isVerified() { return verifiedBy != null; }

    /** The person who entered the fact does not confirm it unless they record why no one else can. */
    public void confirm(UUID confirmer, String exceptionReason) {
        if (confirmer == null) throw new IllegalArgumentException("A confirming user is required");
        if (isVerified()) throw new IllegalStateException("Already confirmed");
        String reason = null;
        if (confirmer.equals(recordedBy)) {
            String r = exceptionReason == null ? "" : exceptionReason.trim();
            if (r.length() < MIN_EXCEPTION_REASON) {
                throw new IllegalArgumentException("The person who entered this must give a reason (at least "
                        + MIN_EXCEPTION_REASON + " characters) to confirm it themselves");
            }
            reason = r.length() > 1000 ? r.substring(0, 1000) : r;
        }
        this.verifiedBy = confirmer;
        this.verifiedAt = Instant.now();
        this.verificationExceptionReason = reason;
    }
}

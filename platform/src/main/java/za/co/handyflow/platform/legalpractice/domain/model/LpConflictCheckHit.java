package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One possible conflict found by a check, kept as it was found (a snapshot) so the record never changes afterwards. */
@Entity
@Table(name = "lp_conflict_check_hits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpConflictCheckHit {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    /** The name that was searched. */
    @Column(name = "subject_name", nullable = false, length = 255)
    private String subjectName;

    /** EXACT_ID | EXACT_NAME | SIMILAR */
    @Column(name = "match_kind", nullable = false, length = 12)
    private String matchKind;

    /** CLIENT (an existing or former client of the firm) | MATTER_PARTY (a party on another matter). */
    @Column(name = "matched_kind", nullable = false, length = 14)
    private String matchedKind;

    @Column(name = "matched_name", nullable = false, length = 255)
    private String matchedName;

    @Column(name = "matched_role", length = 20)
    private String matchedRole;

    /** Plain words for the reviewer, e.g. "Opposing party on matter M-2024-017 for Acme Holdings". */
    @Column(name = "matched_detail", length = 500)
    private String matchedDetail;

    @Column(name = "matched_client_id")
    private UUID matchedClientId;

    @Column(name = "matched_matter_id")
    private UUID matchedMatterId;

    public static LpConflictCheckHit of(TenantId tenantId, UUID checkId, String subjectName, String matchKind, String matchedKind,
                                        String matchedName, String matchedRole, String matchedDetail, UUID matchedClientId,
                                        UUID matchedMatterId) {
        LpConflictCheckHit h = new LpConflictCheckHit();
        h.tenantId = tenantId;
        h.checkId = checkId;
        h.subjectName = subjectName;
        h.matchKind = matchKind;
        h.matchedKind = matchedKind;
        h.matchedName = matchedName;
        h.matchedRole = matchedRole;
        h.matchedDetail = matchedDetail == null ? null : matchedDetail.length() > 500 ? matchedDetail.substring(0, 500) : matchedDetail;
        h.matchedClientId = matchedClientId;
        h.matchedMatterId = matchedMatterId;
        return h;
    }
}

package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.DocRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A document on a matter. The files are its versions (see {@link LpDocumentVersion}); this row says which is current, who has it checked out,
 * whether it is under legal hold, and whether it has been archived. Documents are never deleted.
 */
@Entity
@Table(name = "lp_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "client_id", nullable = false, updatable = false) private UUID clientId;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false, length = 10) private String status = "ACTIVE";
    @Column(name = "current_version", nullable = false) private int currentVersion = 1;

    @Column(name = "legal_hold", nullable = false) private boolean legalHold;
    @Column(name = "hold_reason") private String holdReason;
    @Column(name = "hold_set_by") private UUID holdSetBy;
    @Column(name = "hold_set_by_name") private String holdSetByName;
    @Column(name = "hold_set_at") private Instant holdSetAt;

    @Column(name = "checked_out_by") private UUID checkedOutBy;
    @Column(name = "checked_out_by_name") private String checkedOutByName;
    @Column(name = "checked_out_at") private Instant checkedOutAt;

    @Column(name = "archived_reason") private String archivedReason;

    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    private Long version;

    /** A new document. The first version is added by the caller, so the document starts at version 1. */
    public static LpDocument create(TenantId tenantId, UUID matterId, UUID clientId, String title, String category, UUID by, String byName, Instant now) {
        fail(DocRules.titleProblem(title)); fail(DocRules.categoryProblem(category));
        if (by == null) throw new IllegalArgumentException("A document must say who added it");
        LpDocument d = new LpDocument();
        d.tenantId = tenantId; d.matterId = matterId; d.clientId = clientId;
        d.title = title.trim(); d.category = category;
        d.createdBy = by; d.createdByName = byName; d.createdAt = now; d.updatedAt = now;
        return d;
    }

    public boolean active() { return "ACTIVE".equals(status); }

    public boolean checkedOutByOther(UUID user) { return checkedOutBy != null && !checkedOutBy.equals(user); }

    /** A new version may be added to an active document that is not checked out by someone else. */
    public void requireCanAddVersion(UUID user) {
        requireActive();
        if (checkedOutByOther(user)) throw new IllegalStateException(checkedOutByName + " has this document checked out. They must check it in, or someone with permission can release it");
    }

    /** Records the next version and, if the person adding it had the document checked out, checks it in. Returns the new version number. */
    public int addVersion(UUID user, Instant now) {
        requireCanAddVersion(user);
        currentVersion++;
        if (user.equals(checkedOutBy)) clearCheckout();
        updatedAt = now;
        return currentVersion;
    }

    public void checkOut(UUID user, String name, Instant now) {
        requireActive();
        if (checkedOutBy != null) {
            throw new IllegalStateException(checkedOutBy.equals(user) ? "You already have this document checked out" : checkedOutByName + " already has this document checked out");
        }
        checkedOutBy = user; checkedOutByName = name; checkedOutAt = now; updatedAt = now;
    }

    public void cancelCheckout(UUID user, Instant now) {
        if (checkedOutBy == null) throw new IllegalStateException("This document is not checked out");
        if (!checkedOutBy.equals(user)) throw new IllegalStateException("Only " + checkedOutByName + " can cancel this check-out. Someone with permission can release it");
        clearCheckout(); updatedAt = now;
    }

    /** Releases a check-out held by someone else. The service records who did it and why. */
    public String overrideCheckout(Instant now) {
        if (checkedOutBy == null) throw new IllegalStateException("This document is not checked out");
        String was = checkedOutByName;
        clearCheckout(); updatedAt = now;
        return was;
    }

    public void setHold(String reason, UUID by, String byName, Instant now) {
        requireActive();
        fail(DocRules.reasonProblem(reason));
        if (legalHold) throw new IllegalStateException("This document is already under legal hold");
        legalHold = true; holdReason = reason.trim(); holdSetBy = by; holdSetByName = byName; holdSetAt = now; updatedAt = now;
    }

    public void releaseHold(Instant now) {
        if (!legalHold) throw new IllegalStateException("This document is not under legal hold");
        legalHold = false; holdReason = null; holdSetBy = null; holdSetByName = null; holdSetAt = null; updatedAt = now;
    }

    public void archive(String reason, Instant now) {
        requireActive();
        if (legalHold) throw new IllegalStateException("This document is under legal hold and cannot be archived");
        if (checkedOutBy != null) throw new IllegalStateException("This document is checked out and cannot be archived");
        fail(DocRules.reasonProblem(reason));
        status = "ARCHIVED"; archivedReason = reason.trim(); updatedAt = now;
    }

    public void restore(Instant now) {
        if (active()) throw new IllegalStateException("This document is not archived");
        status = "ACTIVE"; archivedReason = null; updatedAt = now;
    }

    public void edit(String title, String category, Instant now) {
        requireActive();
        fail(DocRules.titleProblem(title)); fail(DocRules.categoryProblem(category));
        this.title = title.trim(); this.category = category; updatedAt = now;
    }

    private void clearCheckout() { checkedOutBy = null; checkedOutByName = null; checkedOutAt = null; }

    private void requireActive() { if (!active()) throw new IllegalStateException("This document is archived. Restore it first"); }

    private static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }
}

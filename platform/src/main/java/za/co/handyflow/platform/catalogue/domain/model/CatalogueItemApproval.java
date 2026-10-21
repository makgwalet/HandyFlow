package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A request to take a draft item live, and the decision on it. Whoever asked can never approve or reject it. */
@Getter
@Entity
@Table(name = "catalogue_item_approvals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueItemApproval {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemApprovalStatus status = ItemApprovalStatus.PENDING;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(length = 255)
    private String note;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @Version
    private Long version;

    public static CatalogueItemApproval request(TenantId tenantId, UUID itemId, String note, UUID by) {
        var a = new CatalogueItemApproval();
        a.tenantId = tenantId;
        a.itemId = itemId;
        a.note = clean(note);
        a.requestedBy = by;
        return a;
    }

    public boolean isOpen() { return status == ItemApprovalStatus.PENDING; }

    /** Approving or rejecting: only while open, and never by the person who asked. */
    private void decide(ItemApprovalStatus to, UUID by, String note) {
        if (!isOpen()) throw new IllegalStateException("This request has already been " + status.name().toLowerCase() + ".");
        if (by == null) throw new AccessDeniedException("Sign in as a user to decide on a request.");
        if (by.equals(requestedBy)) throw new AccessDeniedException("You cannot decide on your own request. Someone else must.");
        status = to; decidedBy = by; decidedAt = Instant.now(); decisionNote = clean(note);
    }

    public void approve(UUID by, String note) { decide(ItemApprovalStatus.APPROVED, by, note); }

    public void reject(UUID by, String note) {
        if (note == null || note.trim().length() < 3) throw new IllegalArgumentException("Give a reason of at least 3 characters.");
        decide(ItemApprovalStatus.REJECTED, by, note);
    }

    /** The requester takes the request back. */
    public void withdraw(UUID by) {
        if (!isOpen()) throw new IllegalStateException("This request has already been " + status.name().toLowerCase() + ".");
        if (by == null || !by.equals(requestedBy)) throw new AccessDeniedException("Only the person who asked can withdraw the request.");
        status = ItemApprovalStatus.WITHDRAWN; decidedBy = by; decidedAt = Instant.now();
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > CatalogueRules.MAX_REASON ? t.substring(0, CatalogueRules.MAX_REASON) : t;
    }
}

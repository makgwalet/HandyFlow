package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One attempt to hand deliveries to Invoicing. STARTED then CREATED or FAILED; LINK_FAILED means the invoice exists in
 * Invoicing but the deliveries could not be tied to it (needs a person); RESOLVED means a person dealt with it.
 */
@Entity
@Table(name = "fuel_invoice_attempts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelInvoiceAttempt {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 20)
    private String status = "STARTED";

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(length = 500)
    private String error;

    @Column(name = "attempted_by")
    private UUID attemptedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fuel_invoice_attempt_deliveries", joinColumns = @JoinColumn(name = "attempt_id"))
    @Column(name = "delivery_id")
    private Set<UUID> deliveryIds = new HashSet<>();

    @Version
    private Long version;

    public static FuelInvoiceAttempt start(TenantId tenantId, UUID customerId, Collection<UUID> deliveryIds, UUID by) {
        if (deliveryIds == null || deliveryIds.isEmpty()) throw new IllegalArgumentException("An invoice attempt needs at least one delivery.");
        FuelInvoiceAttempt a = new FuelInvoiceAttempt();
        a.tenantId = tenantId; a.customerId = customerId; a.attemptedBy = by; a.deliveryIds = new HashSet<>(deliveryIds);
        return a;
    }

    public void created(UUID invoiceId, String invoiceNumber) {
        this.status = "CREATED"; this.invoiceId = invoiceId; this.invoiceNumber = invoiceNumber; this.error = null; this.finishedAt = Instant.now();
    }

    public void failed(String message) {
        this.status = "FAILED"; this.error = cut(message, 500); this.finishedAt = Instant.now();
    }

    /** The invoice exists; the deliveries are not tied to it. Keeps the invoice id so a person can finish the job. */
    public void linkFailed(UUID invoiceId, String invoiceNumber, String message) {
        this.status = "LINK_FAILED"; this.invoiceId = invoiceId; this.invoiceNumber = invoiceNumber;
        this.error = cut(message, 500); this.finishedAt = Instant.now();
    }

    /** A person finished the job (relinked, or released the deliveries). Only open problems can be resolved. */
    public void resolve(UUID by, String note) {
        if (!(("LINK_FAILED".equals(status)) || "STARTED".equals(status))) throw new IllegalStateException("Only an unfinished or half-finished attempt can be resolved.");
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Say what was done, in at least 5 characters.");
        this.status = "RESOLVED"; this.resolvedBy = by; this.resolvedAt = Instant.now(); this.resolutionNote = cut(note.trim(), 300);
        this.finishedAt = this.finishedAt == null ? this.resolvedAt : this.finishedAt;
    }

    /** True when a person needs to look: invoice created but unlinked, or still STARTED long after it should have finished. */
    public boolean needsAttention(Instant now, Duration stale) {
        return "LINK_FAILED".equals(status) || ("STARTED".equals(status) && startedAt.plus(stale).isBefore(now));
    }

    private static String cut(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) : s;
    }
}

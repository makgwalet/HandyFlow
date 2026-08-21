package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Customer — the core aggregate root of the CRM module.
 *
 * ═══════════════════════════════════════════════════════
 * WHY @NoArgsConstructor(access = PROTECTED)?
 * JPA needs a no-arg constructor to hydrate entities from the DB.
 * We make it PROTECTED (not PUBLIC) so application code can never
 * call `new Customer()` directly — they must go through the factory
 * method `Customer.create(...)`.  This is the "always-valid entity"
 * pattern: an entity can never exist in an invalid state.
 *
 * WHY @Getter but no @Setter?
 * All state changes go through explicit domain methods (update,
 * softDelete, restore, changeStatus, addTag, addActivity).
 * This makes the entity's lifecycle readable — you can grep for
 * every place state changes instead of hunting for setters.
 *
 * WHY version field?
 * Optimistic locking. If two users edit the same customer at the
 * same time, the second save will throw OptimisticLockException
 * instead of silently overwriting. V7 defined the column; this
 * annotation wires Hibernate to use it.
 * ═══════════════════════════════════════════════════════
 */
@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    /**
     * JSONB in Postgres — flexible address structure.
     * SA format: street, suburb, city, province, postalCode.
     * Stored as Map<String,String> so no separate @Embeddable needed.
     * WHY Map and not an Address value object?
     * Because address format varies by country and we may add
     * international customers later.  JSONB + Map gives us that
     * flexibility without a schema migration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> address;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * FIX: backlog 4.1 — "no lead ownership/assignment" gap. Nullable —
     * unowned is a valid, meaningful state (a lead nobody has claimed yet),
     * not an error. Defaults to the creating user inside create() below;
     * changed only via assignOwner(), which also records it on the
     * timeline the same way every other field transition on this entity
     * does (see changeStatus/changeStage for the identical shape).
     * <p>
     * No @ManyToOne to a User entity: Customer must not depend on the
     * Identity module's domain model (see CrmFacade's own Javadoc on why
     * cross-module references stay as raw UUIDs/DTOs, never entities).
     */
    @Column(name = "owner_id")
    private UUID ownerId;

    /**
     * WHY Enum stored as STRING?
     * If we store as ORDINAL (0,1,2) and someone reorders the enum,
     * all existing rows silently get the wrong meaning.
     * STRING is always safe and human-readable in the DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType = CustomerType.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    /**
     * FIX: "no lead/pipeline stage tracking" gap. Nullable — only
     * meaningful for LEAD-type customers; a CUSTOMER (already converted)
     * has no pipeline position. Defaults to NEW when a lead is created —
     * see create() below.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_stage", length = 20)
    private LeadStage pipelineStage;

    /**
     * FIX: backlog 4.2 — "no deal value / expected close date" gap.
     * Nullable, and deliberately not restricted to LEAD-type customers the
     * way pipelineStage is: once a lead is WON and converts to a CUSTOMER,
     * this value needs to survive the conversion for win-rate-by-value
     * reporting (see backlog 4.3's own stated goal) — restricting edits to
     * LEAD type the way changeStage() does would make that impossible.
     * Same precision/scale convention as every other money column in this
     * codebase (see AccPaymentReceived.amount).
     */
    @Column(name = "deal_value", precision = 15, scale = 2)
    private BigDecimal dealValue;

    /** FIX: backlog 4.2. See dealValue's Javadoc above — same reasoning applies. */
    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    /**
     * Tags stored in a join table (customer_tags).
     * We use ElementCollection rather than a full @Entity because
     * tags have no identity of their own — they're just strings
     * belonging to a customer.
     *
     * WHY LinkedHashSet?
     * Set = no duplicates (same tag can't be added twice).
     * Linked = predictable order (insertion order), which matters
     * for UI display and test assertions.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "customer_tags", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new LinkedHashSet<>();

    /**
     * Activity timeline — owned by this aggregate.
     * CascadeType.ALL: when we save a customer, Hibernate also saves
     * any new CustomerActivity we added via addActivity().
     * orphanRemoval = false: activities are immutable audit records,
     * never deleted when removed from the collection.
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = false)
    @OrderBy("createdAt DESC")
    private List<CustomerActivity> activities = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /**
     * @Version wires Hibernate optimistic locking to the version column.
     * The DB column is BIGINT DEFAULT 0 (defined in V7).
     */
    @Version
    @Column(nullable = false)
    private Long version;

    // ══════════════════════════════════════════════════════════════════════
    // Factory method — the ONLY way to create a new Customer
    // ══════════════════════════════════════════════════════════════════════

    /**
     * WHY a static factory instead of a constructor?
     * 1. The name `create` communicates intent ("I am creating a customer")
     *    better than `new Customer(...)`.
     * 2. We set createdAt/updatedAt here, not in a @PrePersist, so the
     *    values are visible immediately after calling this method
     *    (useful in tests without hitting the DB).
     * 3. We immediately record a CREATED activity, so the timeline
     *    starts from birth.
     *
     * @param createdBy  The userId who is creating this customer.
     *                   Nullable for system/import flows, but should
     *                   be provided for user-initiated creates.
     */
    public static Customer create(
            TenantId tenantId,
            String name,
            String email,
            String phone,
            Map<String, String> address,
            String taxNumber,
            String notes,
            CustomerType customerType,
            UUID createdBy
    ) {
        var c = new Customer();
        c.tenantId     = Objects.requireNonNull(tenantId, "tenantId required");
        c.name         = Objects.requireNonNull(name, "name required").strip();
        c.email        = normalizeEmail(email);
        c.phone        = phone;
        c.address      = address != null ? Map.copyOf(address) : null;
        c.taxNumber    = taxNumber;
        c.notes        = notes;
        c.customerType = customerType != null ? customerType : CustomerType.CUSTOMER;
        c.status       = CustomerStatus.ACTIVE;
        c.pipelineStage = c.customerType == CustomerType.LEAD ? LeadStage.NEW : null;
        // FIX: backlog 4.1 — default ownership to whoever created the
        // record, same "the creator owns it until reassigned" convention
        // this kind of field takes in every other CRM tool. Deliberately
        // no new factory parameter: createdBy already carries exactly the
        // right value, so reusing it here keeps every existing call site
        // (including CustomerImportService's bulk-import path) working
        // unchanged — imported rows are owned by whoever ran the import,
        // which is the correct default, not "unowned."
        c.ownerId      = createdBy;
        c.createdAt    = Instant.now();
        c.updatedAt    = c.createdAt;

        c.addActivity(CustomerActivity.of(c, ActivityType.CREATED, null, null, createdBy));
        return c;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Domain behaviour methods
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Update mutable fields.  We record a diff in the activity log so
     * "who changed the email and when" is queryable forever.
     *
     * WHY do we build the diff here?
     * Business logic (what counts as a meaningful change, how to record
     * it) belongs in the domain, not in the service layer.
     */
    public void update(
            String name,
            String email,
            String phone,
            Map<String, String> address,
            String taxNumber,
            String notes,
            UUID updatedBy
    ) {
        var changes = new HashMap<String, Object>();
        recordChange(changes, "name",      this.name,      name);
        recordChange(changes, "email",     this.email,     normalizeEmail(email));
        recordChange(changes, "phone",     this.phone,     phone);
        recordChange(changes, "taxNumber", this.taxNumber, taxNumber);
        recordChange(changes, "notes",     this.notes,     notes);

        this.name      = name != null ? name.strip() : this.name;
        this.email     = normalizeEmail(email);
        this.phone     = phone;
        this.address   = address != null ? Map.copyOf(address) : null;
        this.taxNumber = taxNumber;
        this.notes     = notes;
        this.updatedAt = Instant.now();

        if (!changes.isEmpty()) {
            addActivity(CustomerActivity.of(this, ActivityType.UPDATED, changes, null, updatedBy));
        }
    }

    /**
     * Soft-delete.  We now require the userId who performed the delete.
     * The old code passed null — that loses the audit trail entirely.
     *
     * WHY soft-delete and not hard-delete?
     * Customers are referenced by bookings, invoices, quotes.  A hard
     * delete would either cascade-wipe all of that (dangerous) or leave
     * orphaned foreign keys (broken).  Soft-delete keeps the record for
     * audit/compliance while removing it from normal queries.
     */
    public void softDelete(UUID deletedBy) {
        if (this.deletedAt != null) {
            throw new IllegalStateException("Customer is already deleted");
        }
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;      // null is no longer silently accepted
        this.updatedAt = this.deletedAt;
        addActivity(CustomerActivity.of(this, ActivityType.DELETED, null, null, deletedBy));
    }

    /**
     * Restore a soft-deleted customer.
     * New endpoint — previously missing, causing support headaches.
     */
    public void restore(UUID restoredBy) {
        if (this.deletedAt == null) {
            throw new IllegalStateException("Customer is not deleted");
        }
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedAt = Instant.now();
        addActivity(CustomerActivity.of(this, ActivityType.RESTORED, null, null, restoredBy));
    }

    public void changeStatus(CustomerStatus newStatus, UUID changedBy) {
        if (this.status == newStatus) return;
        var payload = Map.of("from", this.status.name(), "to", newStatus.name());
        this.status    = newStatus;
        this.updatedAt = Instant.now();
        addActivity(CustomerActivity.of(this, ActivityType.STATUS_CHANGED, payload, null, changedBy));
    }

    /**
     * FIX: "no lead/pipeline stage tracking" gap. Only valid for
     * LEAD-type customers — a CUSTOMER has no pipeline position, so this
     * throws rather than silently accepting a stage change that wouldn't
     * mean anything (same "enforce it, don't leave it a convention"
     * reasoning IllegalStateException already gets used for elsewhere in
     * this codebase, e.g. Clinic's "cannot log hours on a non-retainer
     * invoice").
     */
    public void changeStage(LeadStage newStage, UUID changedBy) {
        if (this.customerType != CustomerType.LEAD) {
            throw new IllegalStateException("Cannot set a pipeline stage on a non-lead customer");
        }
        if (this.pipelineStage == newStage) return;
        var payload = Map.of(
                "from", this.pipelineStage != null ? this.pipelineStage.name() : "NONE",
                "to", newStage.name()
        );
        this.pipelineStage = newStage;
        this.updatedAt     = Instant.now();
        addActivity(CustomerActivity.of(this, ActivityType.STAGE_CHANGED, payload, null, changedBy));
    }

    /**
     * FIX: backlog 4.1 — "no lead ownership/assignment" gap. Applies to any
     * customer (not restricted to LEAD type the way changeStage() is) —
     * unlike a pipeline stage, "who's the account owner" is a meaningful
     * question for a converted CUSTOMER too. newOwnerId == null is valid
     * and means "unassign" (back to unowned, visible to everyone via the
     * "my leads" OR-owner-IS-NULL filter).
     */
    public void assignOwner(UUID newOwnerId, UUID changedBy) {
        if (Objects.equals(this.ownerId, newOwnerId)) return;
        var payload = Map.of(
                "from", this.ownerId != null ? this.ownerId.toString() : "NONE",
                "to", newOwnerId != null ? newOwnerId.toString() : "NONE"
        );
        this.ownerId   = newOwnerId;
        this.updatedAt = Instant.now();
        addActivity(CustomerActivity.of(this, ActivityType.OWNER_CHANGED, payload, null, changedBy));
    }

    /**
     * FIX: backlog 4.2 — "no deal value / expected close date" gap. Both
     * parameters together, not two separate setters — see DEAL_UPDATED's
     * own Javadoc for why. Either or both may be null (clearing a
     * previously-set value is valid, not an error), so the no-op check is
     * a straight equality on both fields rather than a null-guard.
     */
    public void updateDeal(BigDecimal newDealValue, LocalDate newExpectedCloseDate, UUID changedBy) {
        if (Objects.equals(this.dealValue, newDealValue)
                && Objects.equals(this.expectedCloseDate, newExpectedCloseDate)) {
            return;
        }
        var payload = new HashMap<String, Object>();
        payload.put("dealValue", Map.of(
                "from", this.dealValue != null ? this.dealValue.toPlainString() : "NONE",
                "to", newDealValue != null ? newDealValue.toPlainString() : "NONE"));
        payload.put("expectedCloseDate", Map.of(
                "from", this.expectedCloseDate != null ? this.expectedCloseDate.toString() : "NONE",
                "to", newExpectedCloseDate != null ? newExpectedCloseDate.toString() : "NONE"));

        this.dealValue         = newDealValue;
        this.expectedCloseDate = newExpectedCloseDate;
        this.updatedAt         = Instant.now();
        addActivity(CustomerActivity.of(this, ActivityType.DEAL_UPDATED, payload, null, changedBy));
    }

    public void addTag(String tag, UUID addedBy) {
        Objects.requireNonNull(tag);
        var normalised = tag.strip().toLowerCase();
        if (tags.add(normalised)) {
            addActivity(CustomerActivity.of(this, ActivityType.TAG_ADDED,
                    Map.of("tag", normalised), null, addedBy));
        }
    }

    public void removeTag(String tag, UUID removedBy) {
        Objects.requireNonNull(tag);
        var normalised = tag.strip().toLowerCase();
        if (tags.remove(normalised)) {
            addActivity(CustomerActivity.of(this, ActivityType.TAG_REMOVED,
                    Map.of("tag", normalised), null, removedBy));
        }
    }

    public void addNote(String note, UUID addedBy) {
        Objects.requireNonNull(note, "note must not be null");
        addActivity(CustomerActivity.of(this, ActivityType.NOTE_ADDED, null, note, addedBy));
    }

    /** Called by CrmFacade when a booking is linked cross-module. */
    public void recordBookingLinked(UUID bookingId, UUID triggeredBy) {
        addActivity(CustomerActivity.of(this, ActivityType.BOOKING_LINKED,
                Map.of("bookingId", bookingId.toString()), null, triggeredBy));
    }

    /** Called by CrmFacade when an invoice is linked cross-module. */
    public void recordInvoiceLinked(UUID invoiceId, UUID triggeredBy) {
        addActivity(CustomerActivity.of(this, ActivityType.INVOICE_LINKED,
                Map.of("invoiceId", invoiceId.toString()), null, triggeredBy));
    }

    /**
     * NEW: Called by CrmFacade when the Marketing module records a
     * customer opting in or out of marketing email — same cross-module
     * activity-recording shape as recordBookingLinked/recordInvoiceLinked
     * above, just for a consent change instead of a booking/invoice link.
     * <p>
     * SCOPE NOTE: this only appends a timeline entry, the same lightweight
     * treatment BOOKING_LINKED/INVOICE_LINKED already get. It deliberately
     * does NOT touch CustomerConsent (the formal POPIA Section 11
     * lawful-basis/per-purpose consent record) — partially withdrawing
     * just the "MARKETING" purpose from a consent record that may also
     * cover other purposes (SERVICE_DELIVERY, ANALYTICS, etc.) is real
     * business logic that belongs with whatever already manages
     * CustomerConsent, not invented here without seeing that service. If
     * marketing opt-outs should also update CustomerConsent, that's a
     * distinct, larger piece of work.
     */
    public void recordMarketingConsentChanged(boolean optedIn, UUID triggeredBy) {
        ActivityType type = optedIn ? ActivityType.MARKETING_OPTED_IN : ActivityType.MARKETING_OPTED_OUT;
        addActivity(CustomerActivity.of(this, type, Map.of("optedIn", optedIn), null, triggeredBy));
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return deletedAt == null && status == CustomerStatus.ACTIVE;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════

    private void addActivity(CustomerActivity activity) {
        this.activities.add(activity);
    }

    private static String normalizeEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.strip().toLowerCase();
    }

    private static void recordChange(Map<String, Object> changes, String field,
                                     Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            changes.put(field, Map.of("from", String.valueOf(oldVal),
                    "to",   String.valueOf(newVal)));
        }
    }
}
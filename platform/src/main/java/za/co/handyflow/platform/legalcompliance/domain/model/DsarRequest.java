package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A POPIA data-subject rights request (access/correction/deletion/
 * objection), tracked org-wide regardless of which population the
 * requester belongs to — a superset of crm.PopiaExportService, which
 * only ever handles the ACCESS case for an existing CRM customer.
 * Deliberately NOT linked to crm.Customer by a facade call: a DSAR
 * requester may be an employee, supplier, former customer, or someone
 * who was never a HandyFlow-tracked contact at all, so a hard link would
 * be wrong far more often than it would help. Where the requester IS a
 * current CRM customer, staff can still separately run CRM's own
 * PopiaExportController to produce the actual export artifact — this
 * register tracks the REQUEST and its statutory clock, not the export
 * mechanics for every possible data category.
 * <p>
 * dueDate default of receivedDate + 30 days mirrors
 * crm.PopiaExportService's own class Javadoc phrasing — "typically 30
 * days" — a reasonable operational default, not a confirmed hard
 * statutory deadline. FLAGGED, not silently asserted as legal fact: the
 * exact POPIA response-time requirement (and whether it varies by
 * request type) should be confirmed before this is presented to users as
 * a compliance deadline rather than an internal target.
 */
@Entity
@Table(name = "legalcompliance_dsar_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DsarRequest extends AggregateRoot<DsarRequest> {

    @Column(name = "request_number", nullable = false, length = 30)
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private DsarRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_category", nullable = false, length = 20)
    private DataCategory dataCategory;

    @Column(name = "requester_name", nullable = false, length = 255)
    private String requesterName;

    @Column(name = "requester_email", length = 255)
    private String requesterEmail;

    @Column(name = "requester_contact", length = 100)
    private String requesterContact;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DsarStatus status;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "assigned_to_user_name", length = 255)
    private String assignedToUserName;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static DsarRequest create(TenantId tenantId, String requestNumber, DsarRequestType requestType,
                                     DataCategory dataCategory, String requesterName, String requesterEmail,
                                     String requesterContact, LocalDate receivedDate, UUID createdBy) {
        DsarRequest r = new DsarRequest();
        r.initTenantId(tenantId);
        r.requestNumber = requestNumber;
        r.requestType = requestType;
        r.dataCategory = dataCategory;
        r.requesterName = requesterName;
        r.requesterEmail = requesterEmail;
        r.requesterContact = requesterContact;
        r.receivedDate = receivedDate;
        r.dueDate = receivedDate.plusDays(30);
        r.status = DsarStatus.RECEIVED;
        r.createdBy = createdBy;
        return r;
    }

    public void assign(UUID userId, String userName) {
        assertOpen();
        this.assignedToUserId = userId;
        this.assignedToUserName = userName;
        if (this.status == DsarStatus.RECEIVED) {
            this.status = DsarStatus.IN_PROGRESS;
        }
    }

    public void complete(String resolutionNotes) {
        assertOpen();
        this.status = DsarStatus.COMPLETED;
        this.resolutionNotes = resolutionNotes;
        this.completedDate = LocalDate.now();
    }

    public void reject(String resolutionNotes) {
        assertOpen();
        this.status = DsarStatus.REJECTED;
        this.resolutionNotes = resolutionNotes;
        this.completedDate = LocalDate.now();
    }

    public void withdraw(String resolutionNotes) {
        assertOpen();
        this.status = DsarStatus.WITHDRAWN;
        this.resolutionNotes = resolutionNotes;
        this.completedDate = LocalDate.now();
    }

    public boolean isOverdue(LocalDate today) {
        return (status == DsarStatus.RECEIVED || status == DsarStatus.IN_PROGRESS) && dueDate.isBefore(today);
    }

    private void assertOpen() {
        if (status == DsarStatus.COMPLETED || status == DsarStatus.REJECTED || status == DsarStatus.WITHDRAWN) {
            throw new IllegalStateException("This DSAR request is already closed (status: " + status + ")");
        }
    }

    public boolean isDeleted() { return deletedAt != null; }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }
}

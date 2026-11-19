package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A compliance incident: privacy breach, security, regulatory, operational or ethical. REPORTED → INVESTIGATING → RESOLVED → CLOSED.
 *
 * Where personal information is involved the incident must have a notification assessment (is notification to the Regulator / data subjects
 * required, and why) before it can close. If notification is required, closing also needs the date the Regulator was notified and either the
 * date data subjects were notified or a written reason why they were not. The system does not decide whether notification is required and
 * does not impose a statutory deadline: that judgement belongs to the person assessing, and POPIA s22 says "as soon as reasonably possible".
 * The closer must not be the investigator (unconditional).
 */
@Entity(name = "LcIncident")
@Table(name = "legalcompliance_incidents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incident extends AggregateRoot<Incident> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Enumerated(EnumType.STRING) @Column(name = "incident_type", nullable = false, length = 20) private IncidentType incidentType;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private FindingSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private IncidentStatus status = IncidentStatus.REPORTED;
    @Column(name = "occurred_at") private Instant occurredAt;
    @Column(name = "discovered_at", nullable = false) private Instant discoveredAt;
    @Column(name = "reported_by") private UUID reportedBy;
    @Column(name = "reported_by_name", length = 255) private String reportedByName;
    @Column(name = "control_id") private UUID controlId;
    @Column(name = "personal_info_involved", nullable = false) private boolean personalInfoInvolved;
    @Column(name = "data_subjects_affected") private Integer dataSubjectsAffected;
    @Column(name = "data_categories", columnDefinition = "TEXT") private String dataCategories;
    @Column(name = "investigator_id") private UUID investigatorId;
    @Column(name = "investigator_name", length = 255) private String investigatorName;
    @Column(name = "containment_actions", columnDefinition = "TEXT") private String containmentActions;
    @Column(name = "root_cause", columnDefinition = "TEXT") private String rootCause;
    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "notification_assessed_at") private Instant notificationAssessedAt;
    @Column(name = "notification_assessed_by", length = 255) private String notificationAssessedBy;
    @Column(name = "notification_required") private Boolean notificationRequired;
    @Column(name = "notification_rationale", columnDefinition = "TEXT") private String notificationRationale;
    @Column(name = "regulator_notified_at") private Instant regulatorNotifiedAt;
    @Column(name = "regulator_reference", length = 100) private String regulatorReference;
    @Column(name = "subjects_notified_at") private Instant subjectsNotifiedAt;
    @Column(name = "subjects_not_notified_reason", columnDefinition = "TEXT") private String subjectsNotNotifiedReason;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_by_name", length = 255) private String closedByName;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closure_note", columnDefinition = "TEXT") private String closureNote;

    public static Incident report(TenantId tenantId, String reference, IncidentType type, String title, String description, FindingSeverity severity, Instant occurredAt,
                                  Instant discoveredAt, boolean personalInfo, Integer subjects, String categories, UUID controlId, UUID by, String byName) {
        Incident i = new Incident();
        i.initTenantId(tenantId);
        i.reference = reference;
        i.reportedBy = by;
        i.reportedByName = byName;
        i.apply(type, title, description, severity, occurredAt, discoveredAt, personalInfo, subjects, categories, controlId);
        return i;
    }

    public void update(IncidentType type, String title, String description, FindingSeverity severity, Instant occurredAt, Instant discoveredAt, boolean personalInfo,
                       Integer subjects, String categories, UUID controlId) {
        if (status == IncidentStatus.CLOSED) throw new IllegalStateException("A closed incident cannot be edited");
        apply(type, title, description, severity, occurredAt, discoveredAt, personalInfo, subjects, categories, controlId);
    }

    private void apply(IncidentType type, String title, String description, FindingSeverity severity, Instant occurredAt, Instant discoveredAt, boolean personalInfo,
                       Integer subjects, String categories, UUID controlId) {
        if (type == null) throw new IllegalArgumentException("An incident type is required");
        if (severity == null) throw new IllegalArgumentException("A severity is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (description == null || description.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the incident (at least " + MIN_TEXT + " characters)");
        if (discoveredAt == null) throw new IllegalArgumentException("When the incident was discovered is required");
        if (occurredAt != null && occurredAt.isAfter(discoveredAt)) throw new IllegalArgumentException("An incident cannot have occurred after it was discovered");
        if (subjects != null && subjects < 0) throw new IllegalArgumentException("The number of data subjects cannot be negative");
        this.incidentType = type;
        this.title = title.trim();
        this.description = description.trim();
        this.severity = severity;
        this.occurredAt = occurredAt;
        this.discoveredAt = discoveredAt;
        this.personalInfoInvolved = personalInfo;
        this.dataSubjectsAffected = personalInfo ? subjects : null;
        this.dataCategories = personalInfo && categories != null && !categories.isBlank() ? categories.trim() : null;
        this.controlId = controlId;
    }

    public void startInvestigation(UUID investigatorId, String investigatorName) {
        if (status != IncidentStatus.REPORTED && status != IncidentStatus.INVESTIGATING) throw new IllegalStateException("Investigation can only start on a reported incident — current: " + status);
        if (investigatorName == null || investigatorName.isBlank()) throw new IllegalArgumentException("An investigator is required");
        this.investigatorId = investigatorId;
        this.investigatorName = investigatorName.trim();
        this.status = IncidentStatus.INVESTIGATING;
    }

    public void resolve(String containment, String rootCause, String note) {
        if (status != IncidentStatus.INVESTIGATING) throw new IllegalStateException("Only an incident under investigation can be resolved — current: " + status);
        if (containment == null || containment.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the containment actions (at least " + MIN_TEXT + " characters)");
        if (rootCause == null || rootCause.trim().length() < MIN_TEXT) throw new IllegalArgumentException("State the root cause (at least " + MIN_TEXT + " characters)");
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the resolution (at least " + MIN_TEXT + " characters)");
        this.containmentActions = containment.trim();
        this.rootCause = rootCause.trim();
        this.resolutionNote = note.trim();
        this.resolvedAt = Instant.now();
        this.status = IncidentStatus.RESOLVED;
    }

    /** Records the assessment of whether the Regulator and data subjects must be notified. May be revised until the incident is closed. */
    public void assessNotification(boolean required, String rationale, String byName) {
        if (status == IncidentStatus.CLOSED) throw new IllegalStateException("A closed incident cannot be reassessed");
        if (!personalInfoInvolved) throw new IllegalStateException("Only incidents involving personal information need a notification assessment");
        if (rationale == null || rationale.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Give the rationale for the assessment (at least " + MIN_TEXT + " characters)");
        this.notificationRequired = required;
        this.notificationRationale = rationale.trim();
        this.notificationAssessedAt = Instant.now();
        this.notificationAssessedBy = byName;
        if (!required) { this.regulatorNotifiedAt = null; this.regulatorReference = null; this.subjectsNotifiedAt = null; this.subjectsNotNotifiedReason = null; }
    }

    public void recordRegulatorNotified(Instant at, String reference) {
        requireNotificationRequired();
        if (at == null) throw new IllegalArgumentException("When the Regulator was notified is required");
        if (at.isBefore(discoveredAt)) throw new IllegalArgumentException("The Regulator cannot have been notified before the incident was discovered");
        this.regulatorNotifiedAt = at;
        this.regulatorReference = reference == null || reference.isBlank() ? null : reference.trim();
    }

    public void recordSubjectsNotified(Instant at) {
        requireNotificationRequired();
        if (at == null) throw new IllegalArgumentException("When data subjects were notified is required");
        if (at.isBefore(discoveredAt)) throw new IllegalArgumentException("Data subjects cannot have been notified before the incident was discovered");
        this.subjectsNotifiedAt = at;
        this.subjectsNotNotifiedReason = null;
    }

    /** For when data subjects are deliberately not (yet) notified, for example at the Regulator's or a law-enforcement request. */
    public void deferSubjectsNotification(String reason) {
        requireNotificationRequired();
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Give the reason (at least " + MIN_TEXT + " characters)");
        this.subjectsNotNotifiedReason = reason.trim();
        this.subjectsNotifiedAt = null;
    }

    /** Why the incident cannot close yet, or null. Corrective actions are checked separately by the service. */
    public String closeBlocker() {
        if (status != IncidentStatus.RESOLVED) return "The incident must be resolved first — current: " + status;
        if (personalInfoInvolved) {
            if (notificationAssessedAt == null) return "Personal information is involved, so the notification assessment must be recorded first";
            if (Boolean.TRUE.equals(notificationRequired)) {
                if (regulatorNotifiedAt == null) return "Notification is required: record when the Regulator was notified";
                if (subjectsNotifiedAt == null && subjectsNotNotifiedReason == null) return "Notification is required: record when data subjects were notified, or why they were not";
            }
        }
        return null;
    }

    public void close(UUID by, String byName, String note) {
        String blocker = closeBlocker();
        if (blocker != null) throw new IllegalStateException(blocker);
        if (by != null && by.equals(investigatorId)) throw new IllegalStateException("Segregation of duties: you investigated this incident, so you cannot close it");
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("A closure note of at least " + MIN_TEXT + " characters is required");
        this.closedBy = by;
        this.closedByName = byName;
        this.closedAt = Instant.now();
        this.closureNote = note.trim();
        this.status = IncidentStatus.CLOSED;
    }

    public boolean isOpen() { return status != IncidentStatus.CLOSED; }

    private void requireNotificationRequired() {
        if (status == IncidentStatus.CLOSED) throw new IllegalStateException("A closed incident cannot be changed");
        if (!Boolean.TRUE.equals(notificationRequired)) throw new IllegalStateException("Record an assessment that says notification is required first");
    }
}

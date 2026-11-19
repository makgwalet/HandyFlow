package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One test of one control. PLANNED → IN_PROGRESS → SUBMITTED → COMPLETED (or back to IN_PROGRESS when the reviewer returns it);
 * CANCELLED from PLANNED / IN_PROGRESS.
 * The procedure and expected evidence are snapshotted from the control when the test is scheduled, so the test is judged against
 * what the control said at the time. The reviewer must not be the tester. The result is the tester's; the reviewer accepts or returns it.
 */
@Entity
@Table(name = "legalcompliance_control_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlTest extends AggregateRoot<ControlTest> {

    public static final int MIN_NOTE = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "control_id", nullable = false) private UUID controlId;
    @Column(name = "control_reference", nullable = false, length = 20) private String controlReference;
    @Column(name = "control_title", nullable = false, length = 255) private String controlTitle;
    @Enumerated(EnumType.STRING) @Column(name = "test_type", nullable = false, length = 10) private ControlTestType testType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private ControlTestStatus status = ControlTestStatus.PLANNED;
    @Column(name = "planned_date", nullable = false) private LocalDate plannedDate;
    @Column(name = "procedure_text", columnDefinition = "TEXT") private String procedureText;
    @Column(name = "expected_evidence", columnDefinition = "TEXT") private String expectedEvidence;
    @Enumerated(EnumType.STRING) @Column(name = "design_rating", length = 20) private DesignRating designRating;
    @Column(name = "sample_size") private Integer sampleSize;
    @Column(name = "sample_description", columnDefinition = "TEXT") private String sampleDescription;
    @Column(name = "tester_id") private UUID testerId;
    @Column(name = "tester_name", length = 255) private String testerName;
    @Column(name = "reviewer_id") private UUID reviewerId;
    @Column(name = "reviewer_name", length = 255) private String reviewerName;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "test_notes", columnDefinition = "TEXT") private String testNotes;
    @Enumerated(EnumType.STRING) @Column(length = 10) private TestResult result;
    @Column(name = "submitted_at") private Instant submittedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_snapshot", columnDefinition = "jsonb") private List<Map<String, Object>> evidenceSnapshot = new ArrayList<>();
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_by_name", length = 255) private String reviewedByName;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "TEXT") private String reviewNote;
    @Column(name = "returned_count", nullable = false) private int returnedCount;
    @Column(name = "retest_of_test_id") private UUID retestOfTestId;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_by") private UUID createdBy;

    public static ControlTest schedule(TenantId tenantId, String reference, Control control, ControlTestType type, LocalDate plannedDate,
                                       UUID reviewerId, String reviewerName, UUID retestOf, UUID by) {
        if (control.getStatus() != ControlStatus.ACTIVE) throw new IllegalStateException("Only an active control can be tested");
        if (plannedDate == null) throw new IllegalArgumentException("A planned date is required");
        if (type == ControlTestType.RETEST && retestOf == null) throw new IllegalArgumentException("A retest must say which test it re-tests");
        ControlTest t = new ControlTest();
        t.initTenantId(tenantId);
        t.reference = reference;
        t.controlId = control.getId();
        t.controlReference = control.getReference();
        t.controlTitle = control.getTitle();
        t.testType = type;
        t.plannedDate = plannedDate;
        t.procedureText = control.getProcedureText();
        t.expectedEvidence = control.getExpectedEvidence();
        t.designRating = control.getDesignRating();
        t.reviewerId = reviewerId;
        t.reviewerName = reviewerName;
        t.retestOfTestId = type == ControlTestType.RETEST ? retestOf : null;
        t.createdBy = by;
        return t;
    }

    public void start(UUID testerId, String testerName) {
        if (status != ControlTestStatus.PLANNED) throw new IllegalStateException("Only a planned test can be started — current: " + status);
        if (testerName == null || testerName.isBlank()) throw new IllegalArgumentException("A tester is required");
        if (reviewerId != null && reviewerId.equals(testerId)) throw new IllegalStateException("The assigned reviewer cannot also be the tester");
        this.testerId = testerId;
        this.testerName = testerName;
        this.startedAt = Instant.now();
        this.status = ControlTestStatus.IN_PROGRESS;
    }

    public void recordProgress(Integer sampleSize, String sampleDescription, String notes) {
        if (status != ControlTestStatus.IN_PROGRESS) throw new IllegalStateException("Only a test in progress can be edited — current: " + status);
        if (sampleSize != null && sampleSize < 1) throw new IllegalArgumentException("Sample size must be at least 1");
        this.sampleSize = sampleSize;
        this.sampleDescription = sampleDescription;
        this.testNotes = notes;
    }

    /** evidence = the files attached at this moment (id, name, size). At least one is required for every result, including a pass. */
    public void submit(TestResult result, Integer sampleSize, String sampleDescription, String notes, List<Map<String, Object>> evidence) {
        if (status != ControlTestStatus.IN_PROGRESS) throw new IllegalStateException("Only a test in progress can be submitted — current: " + status);
        if (result == null) throw new IllegalArgumentException("A result is required");
        if (sampleSize == null || sampleSize < 1) throw new IllegalArgumentException("Record how many items were tested (at least 1)");
        if (notes == null || notes.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Test notes of at least " + MIN_NOTE + " characters are required");
        if (evidence == null || evidence.isEmpty()) throw new IllegalArgumentException("Attach at least one piece of evidence before submitting");
        this.result = result;
        this.sampleSize = sampleSize;
        this.sampleDescription = sampleDescription;
        this.testNotes = notes.trim();
        this.evidenceSnapshot = new ArrayList<>(evidence);
        this.submittedAt = Instant.now();
        this.status = ControlTestStatus.SUBMITTED;
    }

    public void approve(UUID reviewerId, String reviewerName, String note) {
        requireSubmitted();
        requireIndependent(reviewerId);
        this.reviewedBy = reviewerId;
        this.reviewedByName = reviewerName;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
        this.status = ControlTestStatus.COMPLETED;
    }

    /** Back to the tester with a reason. The earlier submission (result, evidence snapshot) is cleared; the audit log keeps the history. */
    public void returnForRework(UUID reviewerId, String reviewerName, String note) {
        requireSubmitted();
        requireIndependent(reviewerId);
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say what needs to be fixed (at least " + MIN_NOTE + " characters)");
        this.reviewNote = note.trim();
        this.reviewedBy = reviewerId;
        this.reviewedByName = reviewerName;
        this.reviewedAt = Instant.now();
        this.result = null;
        this.submittedAt = null;
        this.returnedCount++;
        this.status = ControlTestStatus.IN_PROGRESS;
    }

    public void cancel(String reason) {
        if (status != ControlTestStatus.PLANNED && status != ControlTestStatus.IN_PROGRESS)
            throw new IllegalStateException("Only a planned or in-progress test can be cancelled — current: " + status);
        if (reason == null || reason.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A reason of at least " + MIN_NOTE + " characters is required");
        this.cancelReason = reason.trim();
        this.status = ControlTestStatus.CANCELLED;
    }

    public boolean isOpen() { return status == ControlTestStatus.PLANNED || status == ControlTestStatus.IN_PROGRESS || status == ControlTestStatus.SUBMITTED; }

    private void requireSubmitted() { if (status != ControlTestStatus.SUBMITTED) throw new IllegalStateException("Only a submitted test can be reviewed — current: " + status); }
    private void requireIndependent(UUID reviewerId) {
        if (reviewerId != null && reviewerId.equals(testerId)) throw new IllegalStateException("Segregation of duties: the reviewer must not be the tester");
    }
}

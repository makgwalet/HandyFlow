package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Control testing. The only code path that sets a control's operating effectiveness is {@link #approve}.
 * <ul>
 *  <li>The reviewer can never be the tester (always enforced, not behind the SoD flag).</li>
 *  <li>Every result, including a pass, needs at least one evidence file; the files are snapshotted at submission and must still
 *      be attached when the reviewer approves.</li>
 *  <li>A partial or failed result raises exactly one finding in the same transaction.</li>
 *  <li>Only one open test per control (database-enforced).</li>
 * </ul>
 * Evidence files are held by the shared evidence module under sourceModule "legalcompliance", entity type "CONTROL_TEST".
 */
@Service
@RequiredArgsConstructor
public class ControlTestService {

    public static final String SOURCE_MODULE = "legalcompliance";
    public static final String ENTITY_TYPE = "CONTROL_TEST";

    private final ControlTestRepository tests;
    private final ControlRepository controls;
    private final EvidenceFacade evidence;
    private final FindingService findingService;
    private final RegulatoryAuditService audit;

    @Value("${app.legalcompliance.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    @Transactional
    public TestResponse schedule(TenantId tenantId, ScheduleTestRequest req, UUID userId, String userName) {
        Control c = controls.findForTenant(tenantId, req.controlId()).orElseThrow(() -> new ResourceNotFoundException("Control", req.controlId().toString()));
        if (tests.findOpenForControl(tenantId, c.getId()).isPresent())
            throw conflict("OPEN_TEST_EXISTS", "This control already has a test that is planned, in progress or awaiting review");
        if (req.testType() == ControlTestType.RETEST) {
            ControlTest original = tests.findForTenant(tenantId, req.retestOfTestId() == null ? new UUID(0, 0) : req.retestOfTestId())
                    .orElseThrow(() -> bad("Choose the completed test this is a retest of"));
            if (!original.getControlId().equals(c.getId()) || original.getStatus() != ControlTestStatus.COMPLETED || original.getResult() == TestResult.PASS)
                throw bad("A retest must follow a completed partial or failed test of the same control");
        }
        ControlTest t = guarded(() -> ControlTest.schedule(tenantId, String.format("CTST-%05d", tests.nextRef()), c, req.testType(), req.plannedDate(),
                req.reviewerId(), req.reviewerName(), req.retestOfTestId(), userId));
        tests.save(t);
        audit.record(tenantId.getValue(), ENTITY_TYPE, t.getId(), "SCHEDULED", t.getReference() + " " + req.testType() + " for " + c.getReference() + " on " + req.plannedDate(), userId, userName);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional
    public TestResponse start(TenantId tenantId, UUID id, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        Control c = controls.findForTenant(tenantId, t.getControlId()).orElseThrow(() -> new ResourceNotFoundException("Control", t.getControlId().toString()));
        if (enforceSegregationOfDuties && userId != null && userId.equals(c.getOwnerId()))
            throw new HandyFlowException("Segregation of duties: the control owner cannot test their own control", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        guarded(() -> { t.start(userId, userName); return null; });
        tests.save(t);
        audit.record(tenantId.getValue(), ENTITY_TYPE, id, "STARTED", "Tester " + userName, userId, userName);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional
    public TestResponse saveProgress(TenantId tenantId, UUID id, ProgressRequest req, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        requireTester(t, userId);
        guarded(() -> { t.recordProgress(req.sampleSize(), req.sampleDescription(), req.notes()); return null; });
        tests.save(t);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional
    public TestResponse submit(TenantId tenantId, UUID id, SubmitTestRequest req, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        requireTester(t, userId);
        List<Map<String, Object>> snapshot = liveEvidence(tenantId, id).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id().toString());
            m.put("fileName", e.fileName());
            m.put("fileSizeBytes", e.fileSizeBytes());
            m.put("uploadedByName", e.uploadedByName());
            return m;
        }).toList();
        guarded(() -> { t.submit(req.result(), req.sampleSize(), req.sampleDescription(), req.notes(), snapshot); return null; });
        tests.save(t);
        audit.record(tenantId.getValue(), ENTITY_TYPE, id, "SUBMITTED", req.result() + ", sample " + req.sampleSize() + ", " + snapshot.size() + " evidence file(s)", userId, userName);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional
    public TestDetailResponse approve(TenantId tenantId, UUID id, NoteRequest req, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        requireReviewerIndependent(t, userId);
        if (t.getStatus() == ControlTestStatus.SUBMITTED && liveEvidence(tenantId, id).size() < t.getEvidenceSnapshot().size())
            throw conflict("EVIDENCE_REMOVED", "Evidence that was attached at submission has since been detached. Return the test so the tester can re-attach it.");
        guarded(() -> { t.approve(userId, userName, req == null ? null : req.note()); return null; });
        tests.save(t);

        Control c = controls.findForTenant(tenantId, t.getControlId()).orElseThrow(() -> new ResourceNotFoundException("Control", t.getControlId().toString()));
        LocalDate today = LocalDate.now();
        String detail = t.getResult() + " by " + t.getTesterName();
        if (c.isActive()) {
            c.recordTestOutcome(ControlTestRules.effectivenessFor(t.getResult()), Instant.now(), ControlTestRules.nextDue(today, c.getFrequency(), t.getResult()));
            controls.save(c);
            audit.record(tenantId.getValue(), "CONTROL", c.getId(), "TEST_OUTCOME", t.getReference() + ": " + c.getOperatingEffectiveness() + ", next test due " + c.getNextTestDue(), userId, userName);
        } else {
            detail += " (control is no longer active; its effectiveness was not changed)";
        }
        if (ControlTestRules.raisesFinding(t.getResult())) findingService.raiseFromTest(tenantId, t, c, userId, userName);
        audit.record(tenantId.getValue(), ENTITY_TYPE, id, "APPROVED", detail, userId, userName);
        return detail(tenantId, id);
    }

    @Transactional
    public TestResponse returnForRework(TenantId tenantId, UUID id, NoteRequest req, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        requireReviewerIndependent(t, userId);
        TestResult was = t.getResult();
        guarded(() -> { t.returnForRework(userId, userName, req == null ? null : req.note()); return null; });
        tests.save(t);
        audit.record(tenantId.getValue(), ENTITY_TYPE, id, "RETURNED", "Submitted " + was + " returned: " + t.getReviewNote(), userId, userName);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional
    public TestResponse cancel(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        ControlTest t = load(tenantId, id);
        guarded(() -> { t.cancel(req.reason()); return null; });
        tests.save(t);
        audit.record(tenantId.getValue(), ENTITY_TYPE, id, "CANCELLED", req.reason().trim(), userId, userName);
        return TestResponse.of(t, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<TestResponse> list(TenantId tenantId, ControlTestStatus status, UUID controlId) {
        LocalDate today = LocalDate.now();
        return tests.search(tenantId, status, controlId).stream().map(t -> TestResponse.of(t, today)).toList();
    }

    @Transactional(readOnly = true)
    public TestDetailResponse detail(TenantId tenantId, UUID id) {
        ControlTest t = load(tenantId, id);
        List<EvidenceRef> live = liveEvidence(tenantId, id).stream().map(e -> new EvidenceRef(e.id(), e.fileName(), e.fileSizeBytes(), e.uploadedByName())).toList();
        return new TestDetailResponse(TestResponse.of(t, LocalDate.now()), live, findingService.forTest(tenantId, id));
    }

    @Transactional(readOnly = true)
    public TestSummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<ControlTest> all = tests.search(tenantId, null, null);
        List<Control> active = controls.search(tenantId, ControlStatus.ACTIVE, false);
        return new TestSummary(count(all, ControlTestStatus.PLANNED), count(all, ControlTestStatus.IN_PROGRESS), count(all, ControlTestStatus.SUBMITTED),
                all.stream().filter(t -> TestResponse.of(t, today).overdue()).count(),
                active.stream().filter(c -> c.getNextTestDue() != null && c.getNextTestDue().isBefore(today)).count(),
                active.stream().filter(c -> c.getLastTestedAt() == null).count());
    }

    // ── internals ──

    private List<EvidenceResponse> liveEvidence(TenantId tenantId, UUID testId) { return evidence.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, testId); }

    private static long count(List<ControlTest> all, ControlTestStatus s) { return all.stream().filter(t -> t.getStatus() == s).count(); }

    private static void requireTester(ControlTest t, UUID userId) {
        if (t.getTesterId() == null || !t.getTesterId().equals(userId))
            throw new HandyFlowException("Only the tester who started this test can update or submit it", HttpStatus.FORBIDDEN, "NOT_TESTER");
    }

    private static void requireReviewerIndependent(ControlTest t, UUID userId) {
        if (userId != null && userId.equals(t.getTesterId()))
            throw new HandyFlowException("Segregation of duties: you performed this test, so you cannot review it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
    }

    private ControlTest load(TenantId tenantId, UUID id) {
        return tests.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Control test", id.toString()));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        catch (IllegalStateException e) { throw conflict("INVALID_STATE", e.getMessage()); }
    }
    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "VALIDATION"); }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
}

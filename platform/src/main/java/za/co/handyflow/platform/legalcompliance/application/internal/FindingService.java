package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Findings and their remediation. A finding is closed only by someone other than the person who resolved it, and only against a
 * completed, passing retest of the same control that was reviewed after the finding was raised. The segregation check here is
 * unconditional (it is not behind app.legalcompliance.sod.enforce): closure is the control the plan insists on. Any corrective/preventive
 * action raised against the finding must also be verified (or cancelled) before it can close.
 */
@Service
@RequiredArgsConstructor
public class FindingService {

    private static final String FINDING = "FINDING";

    private final FindingRepository findings;
    private final ControlRepository controls;
    private final ControlTestRepository tests;
    private final CapaRepository capas;
    private final RegulatoryAuditService audit;

    /** Idempotent: one finding per source test, however many times this is called. */
    @Transactional
    public Finding raiseFromTest(TenantId tenantId, ControlTest test, Control control, UUID userId, String userName) {
        var existing = findings.findBySource(tenantId, "CONTROL_TEST", test.getId());
        if (existing.isPresent()) return existing.get();
        FindingSeverity severity = ControlTestRules.severityFor(test.getResult());
        Finding f = Finding.fromControlTest(tenantId, String.format("FND-%05d", findings.nextRef()), test.getId(), control.getId(),
                "Control " + control.getReference() + " " + (test.getResult() == TestResult.FAIL ? "failed" : "partially failed") + " testing: " + control.getTitle(),
                test.getTestNotes(), severity, control.getOwnerId(), control.getOwnerName(),
                LocalDate.now().plusDays(ControlTestRules.remediationDays(severity)), userId, userName);
        findings.save(f);
        audit.record(tenantId.getValue(), FINDING, f.getId(), "RAISED", f.getReference() + " (" + severity + ") from test " + test.getReference() + ", due " + f.getDueDate(), userId, userName);
        return f;
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> list(TenantId tenantId, FindingStatus status, UUID controlId, boolean includeClosed) {
        LocalDate today = LocalDate.now();
        return findings.searchCapped(tenantId, status, controlId, includeClosed).stream().map(f -> FindingResponse.of(f, label(tenantId, f.getControlId()), today)).toList();
    }

    @Transactional(readOnly = true)
    public FindingResponse get(TenantId tenantId, UUID id) { return view(tenantId, load(tenantId, id)); }

    @Transactional(readOnly = true)
    public FindingResponse forTest(TenantId tenantId, UUID testId) {
        return findings.findBySource(tenantId, "CONTROL_TEST", testId).map(f -> view(tenantId, f)).orElse(null);
    }

    @Transactional(readOnly = true)
    public FindingSummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<Finding> open = findings.search(tenantId, null, null, false);
        return new FindingSummary(open.size(), open.stream().filter(f -> f.isOverdue(today)).count(),
                open.stream().filter(f -> f.getSeverity() == FindingSeverity.CRITICAL).count(), open.stream().filter(f -> f.getSeverity() == FindingSeverity.HIGH).count(),
                open.stream().filter(f -> f.getStatus() == FindingStatus.RESOLVED).count());
    }

    @Transactional
    public FindingResponse startRemediation(TenantId tenantId, UUID id, RemediationRequest req, UUID userId, String userName) {
        Finding f = load(tenantId, id);
        guarded(() -> { f.startRemediation(req.plan(), req.ownerId(), req.ownerName(), req.dueDate()); return null; });
        findings.save(f);
        audit.record(tenantId.getValue(), FINDING, id, "REMEDIATION_PLANNED", "Owner " + req.ownerName() + ", due " + req.dueDate() + ": " + req.plan().trim(), userId, userName);
        return view(tenantId, f);
    }

    @Transactional
    public FindingResponse resolve(TenantId tenantId, UUID id, FindingNoteRequest req, UUID userId, String userName) {
        Finding f = load(tenantId, id);
        guarded(() -> { f.resolve(userId, userName, req.note()); return null; });
        findings.save(f);
        audit.record(tenantId.getValue(), FINDING, id, "RESOLVED", req.note().trim(), userId, userName);
        return view(tenantId, f);
    }

    @Transactional
    public FindingResponse rejectResolution(TenantId tenantId, UUID id, FindingNoteRequest req, UUID userId, String userName) {
        Finding f = load(tenantId, id);
        requireIndependent(f, userId);
        guarded(() -> { f.rejectResolution(userId, userName, req.note()); return null; });
        findings.save(f);
        audit.record(tenantId.getValue(), FINDING, id, "RESOLUTION_REJECTED", req.note().trim(), userId, userName);
        return view(tenantId, f);
    }

    @Transactional
    public FindingResponse close(TenantId tenantId, UUID id, CloseFindingRequest req, UUID userId, String userName) {
        Finding f = load(tenantId, id);
        requireIndependent(f, userId);
        ControlTest retest = tests.findForTenant(tenantId, req.retestId()).orElseThrow(() -> new ResourceNotFoundException("Control test", req.retestId().toString()));
        if (retest.getStatus() != ControlTestStatus.COMPLETED || retest.getResult() != TestResult.PASS || retest.getTestType() != ControlTestType.RETEST
                || !retest.getControlId().equals(f.getControlId()) || retest.getReviewedAt() == null || !retest.getReviewedAt().isAfter(f.getCreatedAt()))
            throw new HandyFlowException("Closing needs a completed, passing retest of the same control that was reviewed after this finding was raised",
                    HttpStatus.CONFLICT, "RETEST_NOT_ACCEPTABLE");
        long blocking = AssuranceRules.blockingCapas(capas.findByFinding(tenantId, id).stream().map(Capa::getStatus).toList());
        if (blocking > 0) throw new HandyFlowException(blocking + " corrective/preventive action(s) on this finding have not been verified yet", HttpStatus.CONFLICT, "CAPA_INCOMPLETE");
        guarded(() -> { f.close(userId, userName, req.note(), retest.getId()); return null; });
        findings.save(f);
        audit.record(tenantId.getValue(), FINDING, id, "CLOSED", "Verified against retest " + retest.getReference() + ": " + req.note().trim(), userId, userName);
        return view(tenantId, f);
    }

    private void requireIndependent(Finding f, UUID userId) {
        if (userId != null && userId.equals(f.getResolvedBy()))
            throw new HandyFlowException("Segregation of duties: you resolved this finding, so you cannot verify it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
    }

    private FindingResponse view(TenantId tenantId, Finding f) { return FindingResponse.of(f, label(tenantId, f.getControlId()), LocalDate.now()); }

    private String label(TenantId tenantId, UUID controlId) {
        return controlId == null ? null : controls.findForTenant(tenantId, controlId).map(c -> c.getReference() + " — " + c.getTitle()).orElse(null);
    }

    private Finding load(TenantId tenantId, UUID id) {
        return findings.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Finding", id.toString()));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}

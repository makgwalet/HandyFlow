package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditCapaDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID proposer = UUID.randomUUID(), approver = UUID.randomUUID(), owner = UUID.randomUUID(), completer = UUID.randomUUID(), verifier = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 19);
    private final List<Map<String, Object>> evidence = List.of(Map.of("id", UUID.randomUUID().toString(), "fileName", "training-register.pdf"));

    private Audit internalAudit() {
        return Audit.plan(tenant, "AUD-00001", "POPIA readiness audit", AuditType.INTERNAL, "Operator agreements and breach register", "Confirm POPIA obligations are met",
                null, UUID.randomUUID(), "Lead Auditor", null, today, today.plusDays(14), List.of(), proposer, "Proposer");
    }

    private AuditFinding finding(AuditFindingType type) {
        return AuditFinding.raise(tenant, "AFN-00001", UUID.randomUUID(), null, type, "No breach register", "The register was not maintained for six months",
                null, FindingSeverity.HIGH, owner, "Owner", today.plusDays(30), proposer, "Proposer");
    }

    private Capa proposed() {
        return Capa.propose(tenant, "CAPA-00001", null, UUID.randomUUID(), null, CapaType.CORRECTIVE, "Restore the breach register", "Reconstruct the register and train staff",
                "No owner was assigned to maintain it", owner, "Owner", today.plusDays(30), proposer, "Proposer");
    }

    private Capa inProgress() { Capa c = proposed(); c.approve(approver, "Approver", null); c.start(); return c; }
    private Capa completed() { Capa c = inProgress(); c.complete(completer, "Completer", "Register rebuilt and staff trained", evidence); return c; }

    // ── Audit ──

    @Test @DisplayName("an audit moves planned → fieldwork → reporting → completed")
    void auditLifecycle() {
        Audit a = internalAudit();
        assertFalse(a.acceptsFindings());
        a.startFieldwork();
        assertTrue(a.acceptsFindings());
        a.moveToReporting();
        a.complete(AuditRating.NEEDS_IMPROVEMENT, "Two deficiencies found; remediation under way", "Lead");
        assertEquals(AuditStatus.COMPLETED, a.getStatus());
        assertTrue(a.isFinished());
        assertFalse(a.acceptsFindings());
    }

    @Test @DisplayName("completion needs a rating and a real conclusion, and only from reporting")
    void auditCompletionRules() {
        Audit a = internalAudit();
        assertThrows(IllegalStateException.class, () -> a.complete(AuditRating.SATISFACTORY, "Everything was fine and tested", "Lead"));
        a.startFieldwork(); a.moveToReporting();
        assertThrows(IllegalArgumentException.class, () -> a.complete(null, "Everything was fine and tested", "Lead"));
        assertThrows(IllegalArgumentException.class, () -> a.complete(AuditRating.SATISFACTORY, "Fine", "Lead"));
    }

    @Test @DisplayName("an external audit needs its firm; the end date cannot precede the start")
    void auditValidation() {
        assertThrows(IllegalArgumentException.class, () -> Audit.plan(tenant, "AUD-2", "External review", AuditType.EXTERNAL, "Full compliance scope", "Annual assurance",
                null, null, "Partner", " ", today, today, List.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> Audit.plan(tenant, "AUD-3", "Backwards", AuditType.INTERNAL, "Full compliance scope", "Annual assurance",
                null, null, "Lead", null, today, today.minusDays(1), List.of(), null, null));
    }

    @Test @DisplayName("a reporting audit cannot be edited; a finished one cannot be cancelled; overdue tracks the planned end")
    void auditEditAndCancel() {
        Audit a = internalAudit();
        a.startFieldwork(); a.moveToReporting();
        assertThrows(IllegalStateException.class, () -> a.update("New", AuditType.INTERNAL, "Different scope entirely", "Different objectives", null, null, "Lead", null, today, today, List.of()));
        assertTrue(a.isOverdue(today.plusDays(15)));
        assertFalse(a.isOverdue(today.plusDays(14)));
        a.complete(AuditRating.SATISFACTORY, "All objectives met without exception", "Lead");
        assertThrows(IllegalStateException.class, () -> a.cancel("Changed our minds about this"));
        assertFalse(a.isOverdue(today.plusDays(60)));
    }

    // ── Audit finding ──

    @Test @DisplayName("an observation can be closed straight from open")
    void observationClosesFromOpen() {
        AuditFinding f = finding(AuditFindingType.OBSERVATION);
        f.close(verifier, "Verifier", "Acknowledged; no action required");
        assertEquals(AuditFindingStatus.CLOSED, f.getStatus());
    }

    @Test @DisplayName("any other finding must get a management response before it can close")
    void deficiencyNeedsResponse() {
        AuditFinding f = finding(AuditFindingType.DEFICIENCY);
        assertThrows(IllegalStateException.class, () -> f.close(verifier, "Verifier", "Looks fixed to me, thanks"));
        f.respond("We will rebuild the register by month-end", owner, "Owner", today.plusDays(20), proposer, "Responder");
        assertEquals(AuditFindingStatus.IN_REMEDIATION, f.getStatus());
        f.close(verifier, "Verifier", "Verified the register is maintained");
        assertNotNull(f.getClosedAt());
    }

    @Test @DisplayName("the owner and the responder cannot close their own finding")
    void findingSegregation() {
        AuditFinding f = finding(AuditFindingType.DEFICIENCY);
        f.respond("We will rebuild the register by month-end", owner, "Owner", today.plusDays(20), completer, "Responder");
        assertThrows(IllegalStateException.class, () -> f.close(owner, "Owner", "Closing my own finding now"));
        assertThrows(IllegalStateException.class, () -> f.close(completer, "Responder", "Closing my own response now"));
        assertEquals(AuditFindingStatus.IN_REMEDIATION, f.getStatus());
    }

    @Test @DisplayName("a finding needs an owner and a real description; a closed finding cannot be answered again")
    void findingValidation() {
        assertThrows(IllegalArgumentException.class, () -> AuditFinding.raise(tenant, "AFN-9", UUID.randomUUID(), null, AuditFindingType.BREACH, "Title", "Too short",
                null, FindingSeverity.LOW, null, "Owner", today, null, null));
        assertThrows(IllegalArgumentException.class, () -> AuditFinding.raise(tenant, "AFN-9", UUID.randomUUID(), null, AuditFindingType.BREACH, "Title", "A long enough description",
                null, FindingSeverity.LOW, null, " ", today, null, null));
        AuditFinding f = finding(AuditFindingType.OBSERVATION);
        f.close(verifier, "Verifier", "Acknowledged; no action required");
        assertThrows(IllegalStateException.class, () -> f.respond("A later response arrives too late", owner, "Owner", today, null, null));
    }

    // ── CAPA ──

    @Test @DisplayName("an action belongs to exactly one finding")
    void exactlyOneParent() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Capa.propose(tenant, "C1", null, null, null, CapaType.CORRECTIVE, "T", "Reconstruct the register", "No owner was assigned", owner, "Owner", today, null, null));
        assertThrows(IllegalArgumentException.class, () -> Capa.propose(tenant, "C1", id, id, null, CapaType.CORRECTIVE, "T", "Reconstruct the register", "No owner was assigned", owner, "Owner", today, null, null));
        assertNotNull(Capa.propose(tenant, "C1", id, null, null, CapaType.PREVENTIVE, "T", "Reconstruct the register", "No owner was assigned", owner, "Owner", today, null, null));
        assertNotNull(Capa.propose(tenant, "C1", null, null, id, CapaType.CORRECTIVE, "T", "Reconstruct the register", "No owner was assigned", owner, "Owner", today, null, null), "an incident can be the parent");
        assertThrows(IllegalArgumentException.class, () -> Capa.propose(tenant, "C1", null, id, id, CapaType.CORRECTIVE, "T", "Reconstruct the register", "No owner was assigned", owner, "Owner", today, null, null));
    }

    @Test @DisplayName("the proposer and the owner cannot approve or return their own action")
    void approvalSegregation() {
        Capa c = proposed();
        assertThrows(IllegalStateException.class, () -> c.approve(proposer, "Proposer", null));
        assertThrows(IllegalStateException.class, () -> c.approve(owner, "Owner", null));
        assertThrows(IllegalStateException.class, () -> c.returnForRework(owner, "Owner", "Needs more detail please"));
        c.approve(approver, "Approver", "Looks right");
        assertEquals(CapaStatus.APPROVED, c.getStatus());
    }

    @Test @DisplayName("a returned action stays proposed, keeps a count, and can be revised then approved")
    void returnAndRevise() {
        Capa c = proposed();
        c.returnForRework(approver, "Approver", "Say who will train the staff and by when");
        assertEquals(CapaStatus.PROPOSED, c.getStatus());
        assertEquals(1, c.getReturnedCount());
        c.revise(CapaType.CORRECTIVE, "Restore the breach register", "Reconstruct the register; HR trains all staff by month-end", "No owner was assigned to maintain it", owner, "Owner", today.plusDays(25));
        c.approve(approver, "Approver", null);
        assertThrows(IllegalStateException.class, () -> c.revise(CapaType.CORRECTIVE, "x", "Reconstruct the register", "No owner was assigned", owner, "Owner", today));
    }

    @Test @DisplayName("completion needs a note and at least one evidence file, and snapshots the files")
    void completionNeedsEvidence() {
        Capa c = inProgress();
        assertThrows(IllegalArgumentException.class, () -> c.complete(completer, "Completer", "Register rebuilt and staff trained", List.of()));
        assertThrows(IllegalArgumentException.class, () -> c.complete(completer, "Completer", "Done", evidence));
        c.complete(completer, "Completer", "Register rebuilt and staff trained", evidence);
        assertEquals(CapaStatus.COMPLETED, c.getStatus());
        assertEquals(1, c.getEvidenceSnapshot().size());
    }

    @Test @DisplayName("the owner and the person who completed the action cannot verify it")
    void verificationSegregation() {
        Capa c = completed();
        assertThrows(IllegalStateException.class, () -> c.verify(true, owner, "Owner", "Checked and it works well"));
        assertThrows(IllegalStateException.class, () -> c.verify(true, completer, "Completer", "Checked and it works well"));
        c.verify(true, verifier, "Verifier", "Register is current and staff trained");
        assertEquals(CapaStatus.VERIFIED, c.getStatus());
        assertFalse(c.isOpen());
    }

    @Test @DisplayName("an action that did not work goes back to in progress and the reopen count is kept")
    void notEffectiveReopens() {
        Capa c = completed();
        c.verify(false, verifier, "Verifier", "Register still missing two incidents");
        assertEquals(CapaStatus.IN_PROGRESS, c.getStatus());
        assertEquals(1, c.getReopenedCount());
        c.complete(completer, "Completer", "Added the two missing incidents", evidence);
        c.verify(true, verifier, "Verifier", "Register now matches the incident log");
        assertEquals(CapaStatus.VERIFIED, c.getStatus());
    }

    @Test @DisplayName("a verified action cannot be cancelled; others can, with a reason; overdue only while open")
    void cancelAndOverdue() {
        Capa v = completed();
        v.verify(true, verifier, "Verifier", "Register is current and staff trained");
        assertThrows(IllegalStateException.class, () -> v.cancel("Trying to cancel a verified one"));
        Capa c = proposed();
        assertThrows(IllegalArgumentException.class, () -> c.cancel("short"));
        assertTrue(c.isOverdue(today.plusDays(31)));
        c.cancel("Superseded by a wider remediation project");
        assertFalse(c.isOverdue(today.plusDays(31)));
    }
}

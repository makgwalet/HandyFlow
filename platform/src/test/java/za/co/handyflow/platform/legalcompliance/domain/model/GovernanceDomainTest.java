package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID author = UUID.randomUUID(), editor = UUID.randomUUID(), approver = UUID.randomUUID(), investigator = UUID.randomUUID(), closer = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 19);
    private final Instant discovered = Instant.parse("2026-11-19T08:00:00Z");
    private static final String TEXT = "All staff must lock their screens when leaving their desks and report lost devices within one working day.";

    private Policy policy() { return Policy.create(tenant, "POL-00001", "Clear screen policy", PolicyType.POLICY, null, "Compliance Officer", 12, author, "Author"); }
    private PolicyVersion draft(int n) { return PolicyVersion.draft(tenant, UUID.randomUUID(), n, TEXT, n > 1 ? "Added the device reporting rule" : null, author, "Author"); }
    private PolicyVersion published() { PolicyVersion v = draft(1); v.submit(); v.approve(approver, "Approver", null); v.publish("Publisher", today); return v; }

    // ── Policy ──

    @Test @DisplayName("a policy needs a title, an owner and a review period of 1 to 60 months")
    void policyValidation() {
        assertThrows(IllegalArgumentException.class, () -> Policy.create(tenant, "P", " ", PolicyType.POLICY, null, "Owner", 12, null, null));
        assertThrows(IllegalArgumentException.class, () -> Policy.create(tenant, "P", "T", PolicyType.POLICY, null, " ", 12, null, null));
        assertThrows(IllegalArgumentException.class, () -> Policy.create(tenant, "P", "T", PolicyType.POLICY, null, "Owner", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> Policy.create(tenant, "P", "T", PolicyType.POLICY, null, "Owner", 61, null, null));
    }

    @Test @DisplayName("publishing restarts the review clock; a retired policy cannot be changed")
    void policyPublishAndRetire() {
        Policy p = policy();
        UUID version = UUID.randomUUID();
        p.markPublished(version, today);
        assertEquals(version, p.getPublishedVersionId());
        assertEquals(LocalDate.of(2027, 11, 19), p.getNextReviewDue());
        assertThrows(IllegalArgumentException.class, () -> p.retire("short"));
        p.retire("Replaced by the information security policy");
        assertFalse(p.isActive());
        assertThrows(IllegalStateException.class, () -> p.markPublished(version, today));
        assertThrows(IllegalStateException.class, () -> p.update("New", PolicyType.POLICY, null, "Owner", 12));
    }

    // ── Policy version ──

    @Test @DisplayName("a version moves draft → in review → approved → published → superseded")
    void versionLifecycle() {
        PolicyVersion v = draft(1);
        v.submit();
        assertEquals(PolicyVersionStatus.IN_REVIEW, v.getStatus());
        assertNotNull(v.getContentHash());
        v.approve(approver, "Approver", "Fine");
        v.publish("Publisher", today);
        assertEquals(PolicyVersionStatus.PUBLISHED, v.getStatus());
        assertEquals(today, v.getEffectiveDate());
        v.supersede();
        assertEquals(PolicyVersionStatus.SUPERSEDED, v.getStatus());
        assertThrows(IllegalStateException.class, v::supersede);
    }

    @Test @DisplayName("submission needs enough text, and from version 2 a summary of what changed")
    void submissionRules() {
        PolicyVersion tiny = PolicyVersion.draft(tenant, UUID.randomUUID(), 1, "Too short", null, author, "Author");
        assertThrows(IllegalArgumentException.class, tiny::submit);
        PolicyVersion v2 = PolicyVersion.draft(tenant, UUID.randomUUID(), 2, TEXT, null, author, "Author");
        assertThrows(IllegalArgumentException.class, v2::submit);
        v2.edit(TEXT, "Added the device reporting rule", author, "Author");
        v2.submit();
        assertEquals(PolicyVersionStatus.IN_REVIEW, v2.getStatus());
    }

    @Test @DisplayName("text cannot be edited once submitted")
    void contentFrozenAfterSubmit() {
        PolicyVersion v = draft(1);
        v.submit();
        assertThrows(IllegalStateException.class, () -> v.edit("A different text that is long enough to submit for review.", null, author, "Author"));
    }

    @Test @DisplayName("the author cannot approve or return their own version; whoever last edited it counts as the author")
    void approvalSegregation() {
        PolicyVersion v = draft(1);
        v.edit(TEXT + " Managers must also comply.", null, editor, "Editor");
        assertEquals(editor, v.getAuthorId());
        v.submit();
        assertThrows(IllegalStateException.class, () -> v.approve(editor, "Editor", null));
        assertThrows(IllegalStateException.class, () -> v.returnForRework(editor, "Editor", "Needs more detail please"));
        v.approve(author, "Original author", null);
        assertEquals(PolicyVersionStatus.APPROVED, v.getStatus());
    }

    @Test @DisplayName("a returned version goes back to draft, clears its hash, keeps a count, and can be resubmitted")
    void returnAndResubmit() {
        PolicyVersion v = draft(1);
        v.submit();
        String first = v.getContentHash();
        v.returnForRework(approver, "Approver", "Add who to report lost devices to");
        assertEquals(PolicyVersionStatus.DRAFT, v.getStatus());
        assertNull(v.getContentHash());
        assertEquals(1, v.getReturnedCount());
        v.edit(TEXT + " Report lost devices to the IT service desk.", null, author, "Author");
        v.submit();
        assertNotEquals(first, v.getContentHash());
    }

    @Test @DisplayName("only an approved version can be published; the hash identifies the text")
    void publishNeedsApproval() {
        PolicyVersion v = draft(1);
        assertThrows(IllegalStateException.class, () -> v.publish("Publisher", today));
        v.submit();
        assertThrows(IllegalStateException.class, () -> v.publish("Publisher", today));
        assertEquals(PolicyVersion.hash("abc"), PolicyVersion.hash("abc"));
        assertNotEquals(PolicyVersion.hash("abc"), PolicyVersion.hash("abd"));
        assertEquals(64, PolicyVersion.hash("abc").length());
    }

    // ── Campaign and attestation ──

    @Test @DisplayName("a campaign can only be launched on a published version, with a due date that is not in the past")
    void campaignLaunch() {
        PolicyVersion unpublished = draft(1);
        assertThrows(IllegalStateException.class, () -> AttestationCampaign.launch(tenant, "ATT-1", unpublished, "Acknowledge", null, today.plusDays(14), null, today, author, "Author"));
        PolicyVersion v = published();
        assertThrows(IllegalArgumentException.class, () -> AttestationCampaign.launch(tenant, "ATT-1", v, "Acknowledge", null, today.minusDays(1), null, today, author, "Author"));
        AttestationCampaign c = AttestationCampaign.launch(tenant, "ATT-1", v, "Acknowledge", " ", today.plusDays(14), " IT ", today, author, "Author");
        assertEquals(v.getContentHash(), c.getContentHash());
        assertEquals("IT", c.getDepartmentFilter());
        assertNull(c.getInstructions());
        assertTrue(c.isOverdue(today.plusDays(15)));
        assertFalse(c.isOverdue(today.plusDays(14)));
    }

    @Test @DisplayName("a campaign closes once, with a reason")
    void campaignClose() {
        AttestationCampaign c = AttestationCampaign.launch(tenant, "ATT-1", published(), "Acknowledge", null, today.plusDays(14), null, today, author, "Author");
        assertThrows(IllegalArgumentException.class, () -> c.close("Author", "short"));
        c.close("Author", "Superseded by version 2");
        assertFalse(c.isOpen());
        assertThrows(IllegalStateException.class, () -> c.close("Author", "Closing it a second time"));
        assertFalse(c.isOverdue(today.plusDays(60)));
    }

    @Test @DisplayName("accepting records the hash; declining needs a comment; the answer can change")
    void attestationResponses() {
        Attestation a = Attestation.assign(tenant, UUID.randomUUID(), UUID.randomUUID(), "Thandi Nkosi", "thandi@example.com");
        assertTrue(a.isPending());
        assertThrows(IllegalArgumentException.class, () -> a.respond(false, "No", "abc"));
        a.respond(false, "I work offline and cannot comply with rule three", "abc");
        assertEquals(AttestationStatus.DECLINED, a.getStatus());
        a.respond(true, null, "abc");
        assertEquals(AttestationStatus.ACCEPTED, a.getStatus());
        assertEquals("abc", a.getContentHash());
        assertNotNull(a.getRespondedAt());
        assertThrows(IllegalArgumentException.class, () -> Attestation.assign(tenant, UUID.randomUUID(), null, "x", null));
    }

    // ── Incident ──

    private Incident incident(boolean personal) {
        return Incident.report(tenant, "INC-00001", personal ? IncidentType.PRIVACY_BREACH : IncidentType.OPERATIONAL, "Laptop lost", "A laptop was left on a train and has not been recovered",
                FindingSeverity.HIGH, discovered.minusSeconds(3600), discovered, personal, personal ? 120 : 50, personal ? "ID numbers, contact details" : "ignored", null, author, "Reporter");
    }

    private Incident resolved(boolean personal) {
        Incident i = incident(personal);
        i.startInvestigation(investigator, "Investigator");
        i.resolve("Remote wipe issued and credentials reset", "Disk encryption was not enforced on the device", "Device wiped; encryption now enforced on all laptops");
        return i;
    }

    @Test @DisplayName("an incident cannot have occurred after it was discovered; personal-information details are dropped when none is involved")
    void incidentValidation() {
        assertThrows(IllegalArgumentException.class, () -> Incident.report(tenant, "I", IncidentType.SECURITY, "T", "A long enough description", FindingSeverity.LOW,
                discovered.plusSeconds(1), discovered, false, null, null, null, null, null));
        Incident i = incident(false);
        assertNull(i.getDataSubjectsAffected());
        assertNull(i.getDataCategories());
    }

    @Test @DisplayName("an incident with no personal information closes after resolution, by someone other than the investigator")
    void simpleIncidentCloses() {
        Incident i = resolved(false);
        assertNull(i.closeBlocker());
        assertThrows(IllegalStateException.class, () -> i.close(investigator, "Investigator", "Closing my own incident now"));
        i.close(closer, "Closer", "Reviewed the fix and the evidence");
        assertEquals(IncidentStatus.CLOSED, i.getStatus());
        assertFalse(i.isOpen());
    }

    @Test @DisplayName("resolution needs containment, root cause and a resolution, and only from investigation")
    void resolveRules() {
        Incident i = incident(false);
        assertThrows(IllegalStateException.class, () -> i.resolve("Remote wipe issued and credentials reset", "Disk encryption was not enforced", "Device wiped and encryption enforced"));
        i.startInvestigation(investigator, "Investigator");
        assertThrows(IllegalArgumentException.class, () -> i.resolve("Wiped", "Disk encryption was not enforced", "Device wiped and encryption enforced"));
        assertThrows(IllegalArgumentException.class, () -> i.resolve("Remote wipe issued and credentials reset", "Unknown", "Device wiped and encryption enforced"));
    }

    @Test @DisplayName("a personal-information incident cannot close without a notification assessment")
    void personalInfoNeedsAssessment() {
        Incident i = resolved(true);
        assertNotNull(i.closeBlocker());
        assertThrows(IllegalStateException.class, () -> i.close(closer, "Closer", "Trying to close without an assessment"));
        i.assessNotification(false, "The device was encrypted so no readable personal information was exposed", "Privacy Officer");
        assertNull(i.closeBlocker());
        i.close(closer, "Closer", "Assessment recorded and fix verified");
        assertEquals(IncidentStatus.CLOSED, i.getStatus());
    }

    @Test @DisplayName("when notification is required, closing needs the Regulator date and either a data-subject date or a reason")
    void notificationRequired() {
        Incident i = resolved(true);
        i.assessNotification(true, "Unencrypted contact details of 120 people were on the device", "Privacy Officer");
        assertTrue(i.closeBlocker().contains("Regulator"));
        i.recordRegulatorNotified(discovered.plusSeconds(7200), "IR-2026-1234");
        assertTrue(i.closeBlocker().contains("data subjects"));
        i.deferSubjectsNotification("Regulator asked us to hold notification while the police investigate");
        assertNull(i.closeBlocker());
        i.recordSubjectsNotified(discovered.plusSeconds(86400));
        assertNull(i.getSubjectsNotNotifiedReason(), "recording the notification replaces the deferral");
        i.close(closer, "Closer", "Both notifications recorded and fix verified");
        assertEquals(IncidentStatus.CLOSED, i.getStatus());
    }

    @Test @DisplayName("notification dates cannot precede discovery, and cannot be recorded before an assessment says it is required")
    void notificationValidation() {
        Incident i = resolved(true);
        assertThrows(IllegalStateException.class, () -> i.recordRegulatorNotified(discovered.plusSeconds(60), null));
        i.assessNotification(true, "Unencrypted contact details of 120 people were on the device", "Privacy Officer");
        assertThrows(IllegalArgumentException.class, () -> i.recordRegulatorNotified(discovered.minusSeconds(60), null));
        assertThrows(IllegalArgumentException.class, () -> i.recordSubjectsNotified(discovered.minusSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> i.deferSubjectsNotification("Later"));
    }

    @Test @DisplayName("reassessing as not required clears recorded notifications; assessment only applies to personal-information incidents")
    void reassessment() {
        Incident i = resolved(true);
        i.assessNotification(true, "Unencrypted contact details of 120 people were on the device", "Privacy Officer");
        i.recordRegulatorNotified(discovered.plusSeconds(7200), "IR-1");
        i.assessNotification(false, "On review the device was in fact fully encrypted", "Privacy Officer");
        assertNull(i.getRegulatorNotifiedAt());
        assertNull(i.getRegulatorReference());
        assertThrows(IllegalStateException.class, () -> incident(false).assessNotification(true, "Not applicable to this one", "Someone"));
    }

    @Test @DisplayName("a closed incident cannot be edited or reassessed")
    void closedIsFinal() {
        Incident i = resolved(false);
        i.close(closer, "Closer", "Reviewed the fix and the evidence");
        assertThrows(IllegalStateException.class, () -> i.update(IncidentType.OPERATIONAL, "New title", "A long enough description", FindingSeverity.LOW, null, discovered, false, null, null, null));
        assertThrows(IllegalStateException.class, () -> i.startInvestigation(investigator, "Investigator"));
    }
}

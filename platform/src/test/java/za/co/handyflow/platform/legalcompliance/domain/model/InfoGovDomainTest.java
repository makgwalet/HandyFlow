package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InfoGovDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 6, 1);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private static final String TXT = "A sufficiently long text for the rule";

    // ── PIA ──
    private Pia pia(RiskLevel r) { return Pia.create(tenant, "PIA-1", "CCTV", null, TXT, TXT, TXT, TXT, r, alice, "Alice"); }

    @Test @DisplayName("PIA: the assessor cannot approve or reject; another reviewer can")
    void piaSod() {
        Pia p = pia(RiskLevel.LOW); p.submit();
        assertThrows(IllegalStateException.class, () -> p.approve(alice, "Alice", null, 12));
        assertThrows(IllegalStateException.class, () -> p.reject(alice, "Alice", TXT));
        p.approve(bob, "Bob", null, 12);
        assertEquals(PiaStatus.APPROVED, p.getStatus());
        assertNotNull(p.getNextReviewDue());
    }

    @Test @DisplayName("PIA: HIGH residual risk can only be approved with a recorded reason")
    void piaHigh() {
        Pia p = pia(RiskLevel.HIGH); p.submit();
        assertThrows(IllegalArgumentException.class, () -> p.approve(bob, "Bob", "ok", 12));
        p.approve(bob, "Bob", TXT, 6);
        assertEquals(PiaStatus.APPROVED, p.getStatus());
    }

    @Test @DisplayName("PIA: rejection needs a note, and editing a rejected PIA reopens it under the new author")
    void piaRejectEdit() {
        Pia p = pia(RiskLevel.MEDIUM); p.submit();
        assertThrows(IllegalArgumentException.class, () -> p.reject(bob, "Bob", "no"));
        p.reject(bob, "Bob", TXT);
        assertEquals(PiaStatus.REJECTED, p.getStatus());
        p.edit("CCTV v2", null, TXT, TXT, TXT, TXT, RiskLevel.LOW, bob, "Bob");
        assertEquals(PiaStatus.DRAFT, p.getStatus());
        assertEquals(bob, p.getAssessorId());
        p.submit();
        assertThrows(IllegalStateException.class, () -> p.approve(bob, "Bob", null, 12));
    }

    @Test @DisplayName("PIA: an approved assessment cannot be edited; short text is refused")
    void piaGuards() {
        Pia p = pia(RiskLevel.LOW); p.submit(); p.approve(bob, "Bob", null, 12);
        assertThrows(IllegalStateException.class, () -> p.edit("x", null, TXT, TXT, TXT, TXT, RiskLevel.LOW, alice, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> Pia.create(tenant, "PIA-2", "t", null, "short", TXT, TXT, TXT, RiskLevel.LOW, alice, "Alice"));
    }

    // ── PAIA ──
    private PaiaRequest paia() { return PaiaRequest.receive(tenant, "PAIA-1", "Jo Requester", null, null, "Copies of the 2025 board minutes", today, today.plusDays(30), "Alice", alice); }

    @Test @DisplayName("PAIA: one extension only, after the due date, within the cap, with a reason")
    void paiaExtend() {
        PaiaRequest r = paia();
        LocalDate cap = today.plusDays(60);
        assertThrows(IllegalArgumentException.class, () -> r.extend(today.plusDays(30), TXT, cap));
        assertThrows(IllegalArgumentException.class, () -> r.extend(cap.plusDays(1), TXT, cap));
        assertThrows(IllegalArgumentException.class, () -> r.extend(today.plusDays(40), "short", cap));
        r.extend(today.plusDays(40), TXT, cap);
        assertEquals(today.plusDays(40), r.getExtendedUntil());
        assertThrows(IllegalStateException.class, () -> r.extend(today.plusDays(50), TXT, cap));
    }

    @Test @DisplayName("PAIA: a decision needs a reason and a date on or after receipt; refusal is explained")
    void paiaDecide() {
        PaiaRequest r = paia();
        assertThrows(IllegalArgumentException.class, () -> r.decide(PaiaDecision.REFUSED, today.minusDays(1), TXT, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> r.decide(PaiaDecision.REFUSED, today, "no", "Alice"));
        assertThrows(IllegalArgumentException.class, () -> r.decide(PaiaDecision.REFUSED, today, TXT, " "));
        r.decide(PaiaDecision.REFUSED, today.plusDays(3), TXT, "Alice");
        assertEquals(PaiaStatus.DECIDED, r.getStatus());
        assertThrows(IllegalStateException.class, () -> r.decide(PaiaDecision.GRANTED, today, TXT, "Alice"));
    }

    @Test @DisplayName("PAIA: complaints only after a non-granted decision; close needs a decision")
    void paiaComplaintClose() {
        PaiaRequest r = paia();
        assertThrows(IllegalStateException.class, r::close);
        assertThrows(IllegalStateException.class, () -> r.recordComplaint(today, null, null));
        r.decide(PaiaDecision.GRANTED, today.plusDays(2), TXT, "Alice");
        assertThrows(IllegalStateException.class, () -> r.recordComplaint(today.plusDays(3), null, null));
        r.close();
        assertEquals(PaiaStatus.CLOSED, r.getStatus());
        PaiaRequest q = paia();
        q.decide(PaiaDecision.PARTIALLY_GRANTED, today.plusDays(2), TXT, "Alice");
        assertThrows(IllegalArgumentException.class, () -> q.recordComplaint(today.plusDays(1), null, null));
        q.recordComplaint(today.plusDays(5), "IR-77", null);
        assertEquals("IR-77", q.getComplaintReference());
    }

    @Test @DisplayName("PAIA: fee cannot be negative or paid before receipt")
    void paiaFee() {
        PaiaRequest r = paia();
        assertThrows(IllegalArgumentException.class, () -> r.recordFee(BigDecimal.valueOf(-1), null));
        assertThrows(IllegalArgumentException.class, () -> r.recordFee(BigDecimal.TEN, today.minusDays(1)));
        r.recordFee(BigDecimal.TEN, today);
        assertEquals(today, r.getFeePaidOn());
    }

    // ── Filings ──
    private Filing filing() { return Filing.plan(tenant, "FIL-1", FilingType.PAIA_ANNUAL_REPORT, "Annual report", "2025", today.plusDays(20), "Alice", null); }

    @Test @DisplayName("Filing: submission needs prepared state, proof, a date not in the future; acknowledgement needs a reference")
    void filingFlow() {
        Filing f = filing();
        List<Map<String, Object>> proof = List.of(Map.of("id", UUID.randomUUID().toString(), "fileName", "receipt.pdf", "fileSizeBytes", 10, "uploadedByName", "Alice"));
        assertThrows(IllegalStateException.class, () -> f.markSubmitted(today, "Alice", proof, today));
        f.markPrepared();
        assertThrows(IllegalArgumentException.class, () -> f.markSubmitted(today, "Alice", List.of(), today));
        assertThrows(IllegalArgumentException.class, () -> f.markSubmitted(today.plusDays(1), "Alice", proof, today));
        f.markSubmitted(today, "Alice", proof, today);
        assertEquals(FilingStatus.SUBMITTED, f.getStatus());
        assertThrows(IllegalStateException.class, () -> f.update(FilingType.OTHER, "x", null, today, "Alice", null));
        assertThrows(IllegalArgumentException.class, () -> f.acknowledge(" "));
        f.acknowledge("REF-9");
        assertEquals(FilingStatus.ACKNOWLEDGED, f.getStatus());
    }

    // ── FICA ──
    private FicaParty party(FicaPartyType type, RiskLevel risk, boolean pep) { return FicaParty.onboard(tenant, "FICA-1", "Acme (Pty) Ltd", type, "1234", risk, pep, null, alice, "Alice"); }
    private FicaBeneficialOwner owner(boolean verified) { FicaBeneficialOwner o = FicaBeneficialOwner.add(tenant, UUID.randomUUID(), "Owner", BigDecimal.valueOf(60), null); if (verified) o.markVerified(); return o; }

    @Test @DisplayName("FICA party: high risk or PEP starts as enhanced-required; lowering the risk returns it to pending")
    void ficaEnhanced() {
        FicaParty p = party(FicaPartyType.INDIVIDUAL, RiskLevel.HIGH, false);
        assertEquals(FicaPartyStatus.ENHANCED_REQUIRED, p.getStatus());
        p.update("Acme", FicaPartyType.INDIVIDUAL, "1234", RiskLevel.LOW, false, null);
        assertEquals(FicaPartyStatus.PENDING, p.getStatus());
        assertEquals(FicaPartyStatus.ENHANCED_REQUIRED, party(FicaPartyType.INDIVIDUAL, RiskLevel.LOW, true).getStatus());
    }

    @Test @DisplayName("FICA party: the onboarder cannot verify; sanctions screening is required first")
    void ficaSod() {
        FicaParty p = party(FicaPartyType.INDIVIDUAL, RiskLevel.LOW, false);
        assertThrows(IllegalStateException.class, () -> p.verify(alice, "Alice", today, List.of(), null, today.plusMonths(24)));
        assertThrows(IllegalArgumentException.class, () -> p.verify(bob, "Bob", today, List.of(), null, today.plusMonths(24)));
        p.screenedForSanctions(today, today);
        p.verify(bob, "Bob", today, List.of(), null, today.plusMonths(24));
        assertEquals(FicaPartyStatus.VERIFIED, p.getStatus());
        assertEquals(today.plusMonths(24), p.getNextReviewDue());
    }

    @Test @DisplayName("FICA party: a company needs verified beneficial owners")
    void ficaOwners() {
        FicaParty p = party(FicaPartyType.COMPANY, RiskLevel.LOW, false);
        p.screenedForSanctions(today, today);
        assertThrows(IllegalArgumentException.class, () -> p.verify(bob, "Bob", today, List.of(), null, today.plusMonths(24)));
        assertThrows(IllegalArgumentException.class, () -> p.verify(bob, "Bob", today, List.of(owner(false)), null, today.plusMonths(24)));
        p.verify(bob, "Bob", today, List.of(owner(true)), null, today.plusMonths(24));
        assertEquals(FicaPartyStatus.VERIFIED, p.getStatus());
    }

    @Test @DisplayName("FICA party: high risk / PEP needs a senior approver who is not the verifier")
    void ficaSenior() {
        FicaParty p = party(FicaPartyType.INDIVIDUAL, RiskLevel.LOW, true);
        p.screenedForSanctions(today, today);
        assertThrows(IllegalArgumentException.class, () -> p.verify(bob, "Bob", today, List.of(), null, today.plusMonths(6)));
        assertThrows(IllegalStateException.class, () -> p.verify(bob, "Bob", today, List.of(), "bob", today.plusMonths(6)));
        p.verify(bob, "Bob", today, List.of(), "Carol (CEO)", today.plusMonths(6));
        assertEquals("Carol (CEO)", p.getSeniorApprovalName());
    }

    @Test @DisplayName("FICA party: editing a verified party to high risk reopens it; reopen/decline need reasons; identifier hint is short")
    void ficaReopen() {
        FicaParty p = party(FicaPartyType.INDIVIDUAL, RiskLevel.LOW, false);
        p.screenedForSanctions(today, today);
        p.verify(bob, "Bob", today, List.of(), null, today.plusMonths(24));
        p.update("Acme", FicaPartyType.INDIVIDUAL, "1234", RiskLevel.HIGH, false, null);
        assertEquals(FicaPartyStatus.ENHANCED_REQUIRED, p.getStatus());
        assertNull(p.getVerifiedByName());
        assertThrows(IllegalArgumentException.class, () -> p.decline("no"));
        assertThrows(IllegalArgumentException.class, () -> party(FicaPartyType.INDIVIDUAL, RiskLevel.LOW, false).update("A", FicaPartyType.INDIVIDUAL, "8001015009087", RiskLevel.LOW, false, null));
        p.decline(TXT);
        assertEquals(FicaPartyStatus.DECLINED, p.getStatus());
    }

    @Test @DisplayName("Beneficial owner: percentage range, or a control basis when there is none")
    void owners() {
        assertThrows(IllegalArgumentException.class, () -> FicaBeneficialOwner.add(tenant, UUID.randomUUID(), "O", BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> FicaBeneficialOwner.add(tenant, UUID.randomUUID(), "O", BigDecimal.valueOf(100.5), null));
        assertThrows(IllegalArgumentException.class, () -> FicaBeneficialOwner.add(tenant, UUID.randomUUID(), "O", null, null));
        FicaBeneficialOwner o = FicaBeneficialOwner.add(tenant, UUID.randomUUID(), "O", null, "Controls the board");
        o.markVerified();
        o.update("O2", BigDecimal.valueOf(25), null);
        assertFalse(o.isVerified());
    }

    @Test @DisplayName("FICA escalation: the raiser cannot decide; decision needs a reason and a review first")
    void escalation() {
        FicaEscalation e = FicaEscalation.raise(tenant, "ESC-1", null, TXT, alice, "Alice");
        assertThrows(IllegalStateException.class, () -> e.decide(EscalationDecision.REPORTED, TXT, null, bob, "Bob"));
        e.startReview();
        assertThrows(IllegalStateException.class, () -> e.decide(EscalationDecision.REPORTED, TXT, null, alice, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> e.decide(EscalationDecision.REPORTED, "no", null, bob, "Bob"));
        e.decide(EscalationDecision.NOT_REPORTED, TXT, "ignored", bob, "Bob");
        assertEquals(EscalationStatus.DECIDED, e.getStatus());
        assertNull(e.getExternalReference());
    }

    @Test @DisplayName("FICA profile: an accountable institution needs a named officer; non-accountable cannot carry RMCP details")
    void ficaProfile() {
        FicaProfile p = FicaProfile.create(tenant);
        assertThrows(IllegalArgumentException.class, () -> p.update(true, TXT, null, null, null, null, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> p.update(false, TXT, null, UUID.randomUUID(), null, null, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> p.update(true, TXT, "Carol", null, today, today, "Alice"));
        p.update(true, TXT, "Carol", null, today, today.plusYears(1), "Alice");
        assertTrue(p.isAccountableInstitution());
    }

    // ── Third parties ──
    private ThirdParty tp(boolean personal) { return ThirdParty.create(tenant, "TP-1", "Acme Security", ThirdPartyType.SERVICE_PROVIDER, Criticality.HIGH, RiskLevel.MEDIUM, "Alice", null, personal, 12, null, alice, "Alice"); }
    private ThirdPartyItem item(ItemKind k, LocalDate validTo, boolean verified) {
        ThirdPartyItem i = ThirdPartyItem.add(tenant, UUID.randomUUID(), k, "Item " + k, null, null, true, validTo);
        if (verified) i.verify("Bob", today);
        return i;
    }

    @Test @DisplayName("Third party: approval needs verified, unexpired required items and an approver who did not create it")
    void tpApprove() {
        ThirdParty p = tp(false);
        assertThrows(IllegalStateException.class, () -> p.approve(bob, "Bob", today, List.of()));
        List<ThirdPartyItem> pending = List.of(item(ItemKind.DILIGENCE_CHECK, null, false));
        assertThrows(IllegalStateException.class, () -> p.approve(bob, "Bob", today, pending));
        List<ThirdPartyItem> ok = List.of(item(ItemKind.DILIGENCE_CHECK, null, true));
        assertThrows(IllegalStateException.class, () -> p.approve(alice, "Alice", today, ok));
        p.approve(bob, "Bob", today, ok);
        assertEquals(ThirdPartyStatus.APPROVED, p.getStatus());
        assertEquals(today.plusMonths(12), p.getNextReviewDue());
    }

    @Test @DisplayName("Third party: an expired certificate blocks approval")
    void tpExpired() {
        ThirdParty p = tp(false);
        ThirdPartyItem cert = item(ItemKind.CERTIFICATE, today.plusDays(5), true);
        assertTrue(p.blockers(List.of(cert), today).isEmpty());
        assertFalse(p.blockers(List.of(cert), today.plusDays(6)).isEmpty());
        assertThrows(IllegalStateException.class, () -> p.approve(bob, "Bob", today.plusDays(6), List.of(cert)));
    }

    @Test @DisplayName("Third party: handling personal information needs a verified, unexpired operator agreement")
    void tpOperator() {
        ThirdParty p = tp(true);
        ThirdPartyItem check = item(ItemKind.DILIGENCE_CHECK, null, true);
        assertFalse(p.blockers(List.of(check), today).isEmpty());
        ThirdPartyItem oa = item(ItemKind.OPERATOR_AGREEMENT, null, true);
        assertTrue(p.blockers(List.of(check, oa), today).isEmpty());
        p.approve(bob, "Bob", today, List.of(check, oa));
        assertEquals(ThirdPartyStatus.APPROVED, p.getStatus());
    }

    @Test @DisplayName("Third party: starting to handle personal information suspends an approved party")
    void tpGainPersonalInfo() {
        ThirdParty p = tp(false);
        p.approve(bob, "Bob", today, List.of(item(ItemKind.DILIGENCE_CHECK, null, true)));
        p.update("Acme Security", ThirdPartyType.SERVICE_PROVIDER, Criticality.HIGH, RiskLevel.MEDIUM, "Alice", null, true, 12, null);
        assertEquals(ThirdPartyStatus.SUSPENDED, p.getStatus());
    }

    @Test @DisplayName("Third party: review, suspend and offboard need reasons; offboarded is final")
    void tpLifecycle() {
        ThirdParty p = tp(false);
        p.approve(bob, "Bob", today, List.of(item(ItemKind.DILIGENCE_CHECK, null, true)));
        assertThrows(IllegalArgumentException.class, () -> p.recordReview(today, RiskLevel.HIGH, "ok"));
        p.recordReview(today.plusMonths(6), RiskLevel.HIGH, TXT);
        assertEquals(RiskLevel.HIGH, p.getRiskRating());
        assertEquals(today.plusMonths(18), p.getNextReviewDue());
        assertThrows(IllegalArgumentException.class, () -> p.suspend("no"));
        p.suspend(TXT);
        assertThrows(IllegalStateException.class, () -> p.recordReview(today, null, TXT));
        p.offboard(TXT);
        assertThrows(IllegalStateException.class, () -> p.offboard(TXT));
        assertThrows(IllegalStateException.class, () -> p.update("x", ThirdPartyType.SUPPLIER, Criticality.LOW, RiskLevel.LOW, "A", null, false, 12, null));
    }

    @Test @DisplayName("Third-party item: certificate needs expiry; expired cannot be verified; edit resets verification")
    void tpItem() {
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyItem.add(tenant, UUID.randomUUID(), ItemKind.CERTIFICATE, "Tax clearance", null, null, true, null));
        ThirdPartyItem expired = ThirdPartyItem.add(tenant, UUID.randomUUID(), ItemKind.INSURANCE, "Cover", null, null, true, today.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> expired.verify("Bob", today));
        ThirdPartyItem i = item(ItemKind.CERTIFICATE, today.plusDays(90), true);
        assertEquals(ItemStatus.VERIFIED, i.getStatus());
        i.update(ItemKind.CERTIFICATE, "Tax clearance", null, null, true, today.plusDays(100));
        assertEquals(ItemStatus.PENDING, i.getStatus());
        assertNull(i.getVerifiedByName());
        assertThrows(IllegalArgumentException.class, () -> i.reject("no"));
        i.reject(TXT);
        assertEquals(ItemStatus.REJECTED, i.getStatus());
    }
}

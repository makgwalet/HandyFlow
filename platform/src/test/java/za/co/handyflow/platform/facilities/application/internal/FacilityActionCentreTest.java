package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.model.FacilityCorrectiveAction;
import za.co.handyflow.platform.facilities.dto.ActionCentreResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityActionCentreTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FacilityTime.today();

    private FacilityComplianceCertificate cert(int expiresInDays) {
        return FacilityComplianceCertificate.create(tenant, UUID.randomUUID(), null, "ELECTRICAL_COC", "C-" + expiresInDays,
                "Sparky", today.plusDays(expiresInDays).minusYears(1), today.plusDays(expiresInDays), null);
    }

    private FacilityCorrectiveAction action(int dueInDays, boolean critical) {
        return FacilityCorrectiveAction.create(tenant, "MANUAL", null, UUID.randomUUID(), null, "Fix " + dueInDays,
                "NORMAL", critical, today.plusDays(dueInDays), null);
    }

    private ActionCentreResponse build(List<FacilityComplianceCertificate> c, List<FacilityCorrectiveAction> a) {
        return FacilityActionCentre.build(c, a, today, 30);
    }

    @Test
    void aLapsedCertificateIsCritical() {
        ActionCentreResponse r = build(List.of(cert(-3)), List.of());
        assertEquals(1, r.critical());
        assertEquals("CERTIFICATE_LAPSED", r.items().get(0).type());
        assertTrue(r.items().get(0).detail().contains("3 day"));
    }

    @Test
    void expiringCertificatesAreHighWithinTwoWeeksAndMediumBeyond() {
        ActionCentreResponse r = build(List.of(cert(14), cert(15), cert(30), cert(31)), List.of());
        assertEquals(1, r.high());
        assertEquals(2, r.medium());
        assertEquals(3, r.items().size()); // 31 days is outside the warning window
    }

    @Test
    void aCertificateExpiringTodayIsStillValidButUrgent() {
        ActionCentreResponse r = build(List.of(cert(0)), List.of());
        assertEquals("HIGH", r.items().get(0).severity());
    }

    @Test
    void revokedCertificatesAreIgnored() {
        FacilityComplianceCertificate c = cert(-10);
        c.revoke("Replaced by a new one");
        assertTrue(build(List.of(c), List.of()).items().isEmpty());
    }

    @Test
    void anOpenCriticalActionIsCriticalEvenBeforeItsDueDate() {
        ActionCentreResponse r = build(List.of(), List.of(action(1, true)));
        assertEquals(1, r.critical());
        assertEquals("ACTION_CRITICAL_OPEN", r.items().get(0).type());
    }

    @Test
    void overdueActionsAreHighUntilAWeekLateThenCritical() {
        ActionCentreResponse r = build(List.of(), List.of(action(-1, false), action(-7, false), action(-8, false)));
        assertEquals(2, r.high());
        assertEquals(1, r.critical());
        assertTrue(r.items().stream().allMatch(i -> "ACTION_OVERDUE".equals(i.type())));
    }

    @Test
    void onScheduleNonCriticalActionsAreNotAlerts() {
        assertTrue(build(List.of(), List.of(action(0, false), action(10, false))).items().isEmpty());
    }

    @Test
    void finishedActionsAreIgnored() {
        FacilityCorrectiveAction a = action(-20, true);
        a.close("Done");
        assertTrue(build(List.of(), List.of(a)).items().isEmpty());
    }

    @Test
    void rankedBySeverityThenDueDate() {
        ActionCentreResponse r = build(List.of(cert(20), cert(-1)), List.of(action(-3, false), action(-30, false)));
        assertEquals(List.of("CRITICAL", "CRITICAL", "HIGH", "MEDIUM"), r.items().stream().map(ActionCentreResponse.Item::severity).toList());
        // within CRITICAL the 30-days-late action (older due date) comes before the lapsed certificate
        assertEquals("ACTION_OVERDUE", r.items().get(0).type());
        assertEquals("CERTIFICATE_LAPSED", r.items().get(1).type());
        assertEquals(2, r.critical());
        assertEquals(1, r.high());
        assertEquals(1, r.medium());
    }

    @Test
    void theListIsCappedButCountsAreNot() {
        List<FacilityCorrectiveAction> many = new java.util.ArrayList<>();
        for (int i = 0; i < 250; i++) many.add(action(-2, false));
        ActionCentreResponse r = build(List.of(), many);
        assertEquals(FacilityActionCentre.MAX_ITEMS, r.items().size());
        assertEquals(250, r.high());
    }

    @Test
    void failureRulesGiveCriticalFailuresAHighPriorityAndAShortDeadline() {
        assertEquals("HIGH", FacilityActionRules.priorityForFailure(true));
        assertEquals("NORMAL", FacilityActionRules.priorityForFailure(false));
        assertEquals(today.plusDays(2), FacilityActionRules.dueDateForFailure(true, today));
        assertEquals(today.plusDays(14), FacilityActionRules.dueDateForFailure(false, today));
    }

    // ── vendor documents ─────────────────────────────────────────────────────

    private za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument vendorDoc(String type, int expiresInDays, UUID vendor) {
        return za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument.create(tenant, vendor, type, "R1", null, null,
                today.plusDays(expiresInDays), null, null);
    }

    @Test
    void aLapsedRequiredVendorDocumentIsCriticalAndNamesTheVendor() {
        UUID v = UUID.randomUUID();
        ActionCentreResponse r = FacilityActionCentre.build(List.of(), List.of(),
                List.of(vendorDoc("COIDA_LETTER", -2, v)), java.util.Map.of(v, "ACME Plumbing"), today, 30);
        assertEquals(1, r.critical());
        ActionCentreResponse.Item i = r.items().get(0);
        assertEquals("VENDOR_DOCUMENT_LAPSED", i.type());
        assertEquals("VENDOR", i.entityType());
        assertEquals(v, i.entityId());
        assertTrue(i.title().startsWith("ACME Plumbing"));
        assertTrue(i.detail().contains("cannot be assigned work"));
    }

    @Test
    void aLapsedOptionalVendorDocumentIsOnlyHigh() {
        ActionCentreResponse r = FacilityActionCentre.build(List.of(), List.of(),
                List.of(vendorDoc("TRADE_LICENCE", -2, UUID.randomUUID())), java.util.Map.of(), today, 30);
        assertEquals(1, r.high());
        assertEquals(0, r.critical());
    }

    @Test
    void expiringVendorDocumentsFollowTheSameWindowsAsCertificates() {
        UUID v = UUID.randomUUID();
        ActionCentreResponse r = FacilityActionCentre.build(List.of(), List.of(),
                List.of(vendorDoc("TAX_CLEARANCE", 10, v), vendorDoc("TAX_CLEARANCE", 25, v), vendorDoc("TAX_CLEARANCE", 45, v)),
                java.util.Map.of(), today, 30);
        assertEquals(1, r.high());
        assertEquals(1, r.medium());
        assertEquals(2, r.items().size());
    }

    @Test
    void revokedVendorDocumentsAreIgnored() {
        var d = vendorDoc("COIDA_LETTER", -2, UUID.randomUUID());
        d.revoke("replaced");
        assertTrue(FacilityActionCentre.build(List.of(), List.of(), List.of(d), java.util.Map.of(), today, 30).items().isEmpty());
    }

    // ── lapsed certificates with an action; technician licences ──────────────

    @Test
    void aLapsedCertificateWithAnOpenActionShowsOnlyAsTheAction() {
        FacilityComplianceCertificate c = cert(-3);
        FacilityCorrectiveAction a = FacilityCorrectiveAction.create(tenant, "CERTIFICATE", c.getId(), c.getSiteId(), null,
                "Certificate lapsed: renew", "URGENT", true, today.plusDays(7), null);
        ActionCentreResponse r = build(List.of(c), List.of(a));
        assertEquals(1, r.items().size());
        assertEquals("ACTION_CRITICAL_OPEN", r.items().get(0).type());
    }

    @Test
    void aLapsedCertificateWhoseActionIsClosedShowsAgain() {
        FacilityComplianceCertificate c = cert(-3);
        FacilityCorrectiveAction a = FacilityCorrectiveAction.create(tenant, "CERTIFICATE", c.getId(), c.getSiteId(), null,
                "Certificate lapsed: renew", "URGENT", true, today.plusDays(7), null);
        a.close("Done");
        ActionCentreResponse r = build(List.of(c), List.of(a));
        assertEquals("CERTIFICATE_LAPSED", r.items().get(0).type());
    }

    private za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence licence(int expiresInDays, UUID tech) {
        return za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence.create(tenant, tech, "GAS_INSTALLER", "L1",
                null, null, today.plusDays(expiresInDays));
    }

    private ActionCentreResponse withLicences(List<za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence> l,
                                              java.util.Map<UUID, String> names) {
        return FacilityActionCentre.build(List.of(), List.of(), List.of(), java.util.Map.of(), List.of(), java.util.Map.of(), l, names, today, 30);
    }

    @Test
    void aLapsedTechnicianLicenceIsHighAndNamesTheTechnician() {
        UUID t = UUID.randomUUID();
        ActionCentreResponse r = withLicences(List.of(licence(-2, t)), java.util.Map.of(t, "Thabo"));
        assertEquals(1, r.high());
        ActionCentreResponse.Item i = r.items().get(0);
        assertEquals("TECHNICIAN_LICENCE_LAPSED", i.type());
        assertEquals("TECHNICIAN", i.entityType());
        assertEquals(t, i.entityId());
        assertTrue(i.title().startsWith("Thabo"));
    }

    @Test
    void expiringTechnicianLicencesFollowTheSameWindowsAsCertificates() {
        UUID t = UUID.randomUUID();
        ActionCentreResponse r = withLicences(List.of(licence(10, t), licence(25, t), licence(45, t)), java.util.Map.of());
        assertEquals(1, r.high());
        assertEquals(1, r.medium());
        assertEquals(2, r.items().size());
    }

    @Test
    void revokedTechnicianLicencesAreIgnored() {
        var l = licence(-2, UUID.randomUUID());
        l.revoke("Struck off");
        assertTrue(withLicences(List.of(l), java.util.Map.of()).items().isEmpty());
    }
}

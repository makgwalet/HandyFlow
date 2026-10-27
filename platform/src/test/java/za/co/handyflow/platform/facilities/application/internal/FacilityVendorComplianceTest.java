package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityVendorComplianceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID vendor = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 10, 27);

    private FacilityVendorDocument doc(String type, int expiresInDays) {
        return FacilityVendorDocument.create(tenant, vendor, type, "R-1", "Insurer", today.minusYears(1),
                today.plusDays(expiresInDays), null, null);
    }

    private List<FacilityVendorDocument> allRequired(int days) {
        return new ArrayList<>(List.of(doc("PUBLIC_LIABILITY_INSURANCE", days), doc("COIDA_LETTER", days), doc("TAX_CLEARANCE", days)));
    }

    @Test
    void aVendorWithNoDocumentsIsNonCompliantAndEverythingIsMissing() {
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(List.of(), today);
        assertEquals("NON_COMPLIANT", r.status());
        assertEquals(FacilityVendorCompliance.REQUIRED, r.missing());
        assertEquals(3, r.problems().size());
    }

    @Test
    void allRequiredDocumentsInForceIsCompliant() {
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(allRequired(200), today);
        assertEquals("COMPLIANT", r.status());
        assertTrue(r.problems().isEmpty());
    }

    @Test
    void anExpiredRequiredDocumentMakesTheVendorNonCompliant() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.set(1, doc("COIDA_LETTER", -1));
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(docs, today);
        assertTrue(r.isNonCompliant());
        assertEquals(List.of("COIDA_LETTER"), r.lapsed());
        assertEquals(List.of("COIDA_LETTER has expired"), r.problems());
    }

    @Test
    void aDocumentExpiringTodayStillCounts() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.set(0, doc("PUBLIC_LIABILITY_INSURANCE", 0));
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(docs, today);
        assertEquals("EXPIRING_SOON", r.status());
        assertEquals(0, r.expiring().get(0).daysLeft());
    }

    @Test
    void expiringWithinThirtyDaysIsFlaggedButStillEligible() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.set(2, doc("TAX_CLEARANCE", 30));
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(docs, today);
        assertEquals("EXPIRING_SOON", r.status());
        assertFalse(r.isNonCompliant());
        docs.set(2, doc("TAX_CLEARANCE", 31));
        assertEquals("COMPLIANT", FacilityVendorCompliance.evaluate(docs, today).status());
    }

    @Test
    void renewingBeforeExpiryNeverLeavesAGap() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.add(doc("COIDA_LETTER", -5));   // old one, long expired
        docs.add(doc("COIDA_LETTER", 365));  // renewal
        assertEquals("COMPLIANT", FacilityVendorCompliance.evaluate(docs, today).status());
    }

    @Test
    void aRevokedDocumentDoesNotCount() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.get(0).revoke("Policy cancelled");
        FacilityVendorCompliance.Result r = FacilityVendorCompliance.evaluate(docs, today);
        assertEquals(List.of("PUBLIC_LIABILITY_INSURANCE"), r.missing());
    }

    @Test
    void optionalDocumentTypesDoNotAffectCompliance() {
        List<FacilityVendorDocument> docs = allRequired(200);
        docs.add(doc("TRADE_LICENCE", -100));
        docs.add(doc("BEE_CERTIFICATE", -100));
        assertEquals("COMPLIANT", FacilityVendorCompliance.evaluate(docs, today).status());
    }

    // ── document rules ───────────────────────────────────────────────────────

    @Test
    void documentsValidateTheirInput() {
        assertThrows(IllegalArgumentException.class, () -> FacilityVendorDocument.create(tenant, vendor, "BOGUS", null, null, null, today, null, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityVendorDocument.create(tenant, vendor, "COIDA_LETTER", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityVendorDocument.create(tenant, vendor, "COIDA_LETTER", null, null, today, today.minusDays(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityVendorDocument.create(tenant, vendor, "COIDA_LETTER", null, null, null, today, new java.math.BigDecimal("-1"), null));
        assertNotNull(FacilityVendorDocument.create(tenant, vendor, "other", null, null, null, null, null, null));
    }

    @Test
    void revokingNeedsAReasonAndHappensOnce() {
        FacilityVendorDocument d = doc("COIDA_LETTER", 100);
        assertThrows(IllegalArgumentException.class, () -> d.revoke(" "));
        d.revoke("Wrong company");
        assertFalse(d.isInForce(today));
        assertThrows(IllegalStateException.class, () -> d.revoke("again"));
    }
}

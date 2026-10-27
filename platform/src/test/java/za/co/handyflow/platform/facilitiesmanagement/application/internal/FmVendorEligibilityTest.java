package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendorDocument;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The rule the work-order assignment gate relies on: approved AND every required document in force. */
class FmVendorEligibilityTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FmTime.today();
    private final FmVendor vendor = FmVendor.create(tenant, "ACME", "HVAC", null, null, null, null);
    private final List<FmVendorDocument> docs = new ArrayList<>();
    private final FmVendorDocumentRepository repo = (FmVendorDocumentRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmVendorDocumentRepository.class}, (p, m, a) -> {
                if ("findForVendor".equals(m.getName())) return docs;
                throw new UnsupportedOperationException(m.getName());
            });
    private final FmVendorEligibility eligibility = new FmVendorEligibility(repo);

    private void allDocs(int days) {
        for (String t : FmVendorCompliance.REQUIRED)
            docs.add(FmVendorDocument.create(tenant, vendor.getId(), t, "R", null, null, today.plusDays(days), null, null));
    }

    @Test
    void aPendingVendorWithNoDocumentsHasAllTheProblems() {
        List<String> p = eligibility.problems(tenant, vendor);
        assertEquals(4, p.size());
        assertTrue(p.get(0).startsWith("vendor is PENDING"));
    }

    @Test
    void anApprovedVendorWithDocumentsInForceHasNone() {
        allDocs(100);
        vendor.approve(UUID.randomUUID());
        assertTrue(eligibility.problems(tenant, vendor).isEmpty());
    }

    @Test
    void aSuspendedVendorShowsItsReasonEvenWithGoodPaperwork() {
        allDocs(100);
        vendor.approve(UUID.randomUUID());
        vendor.suspend("Safety audit failed");
        List<String> p = eligibility.problems(tenant, vendor);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("SUSPENDED") && p.get(0).contains("Safety audit failed"));
    }

    @Test
    void anExpiredRequiredDocumentBlocksAnApprovedVendor() {
        allDocs(100);
        docs.set(0, FmVendorDocument.create(tenant, vendor.getId(), "PUBLIC_LIABILITY_INSURANCE", "R", null, null, today.minusDays(1), null, null));
        vendor.approve(UUID.randomUUID());
        assertEquals(List.of("PUBLIC_LIABILITY_INSURANCE has expired"), eligibility.problems(tenant, vendor));
    }
}

package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.DisposalRules;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDisposalRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalRulesTest {
    TenantId tenant = TenantId.generate();
    UUID asset = UUID.randomUUID(), asker = UUID.randomUUID(), boss = UUID.randomUUID();

    EarthDisposalRequest scrap() { return EarthDisposalRequest.request(tenant, asset, "scrapped", "Engine seized, beyond repair", null, null, asker); }

    @Test void reasonsAreCheckedAndNormalised() {
        assertEquals("SOLD", DisposalRules.reason(" sold "));
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.reason("gift"));
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.reason(null));
    }

    @Test void aSaleOrTransferNeedsWhoItWentTo() {
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.counterparty("SOLD", " "));
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.counterparty("TRANSFERRED", null));
        assertNull(DisposalRules.counterparty("SCRAPPED", ""));
        assertEquals("Acme Plant", DisposalRules.counterparty("SOLD", " Acme Plant "));
    }

    @Test void detailsAndAmountsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.details("no"));
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.details("x".repeat(2001)));
        assertThrows(IllegalArgumentException.class, () -> DisposalRules.proceeds(new BigDecimal("-1")));
        assertEquals(new BigDecimal("1000.50"), DisposalRules.proceeds(new BigDecimal("1000.499")));
        assertNull(DisposalRules.proceeds(null));
    }

    @Test void anOpenDeploymentOrHireOrUninvoicedChargeBlocksDisposal() {
        assertTrue(DisposalRules.blockers(AssetStatus.AVAILABLE, false, 0).isEmpty());
        assertEquals(1, DisposalRules.blockers(AssetStatus.DEPLOYED, true, 0).size());
        assertEquals(1, DisposalRules.blockers(AssetStatus.HIRED_OUT, false, 0).size());
        assertEquals(1, DisposalRules.blockers(AssetStatus.AVAILABLE, true, 0).size());
        List<String> two = DisposalRules.blockers(AssetStatus.DEPLOYED, true, 3);
        assertEquals(2, two.size());
        assertTrue(two.get(1).startsWith("3 hire charges have not been invoiced"));
        assertTrue(DisposalRules.blockers(AssetStatus.RETIRED, false, 0).get(0).contains("already retired"));
        assertThrows(IllegalStateException.class, () -> DisposalRules.requireNoBlockers(two));
    }

    @Test void evidenceIsRequired() {
        assertThrows(IllegalStateException.class, () -> DisposalRules.requireEvidence(0));
        assertDoesNotThrow(() -> DisposalRules.requireEvidence(1));
    }

    @Test void theRequesterCannotApproveOrReject() {
        assertThrows(IllegalStateException.class, () -> scrap().approve(null, asker));
        assertThrows(IllegalStateException.class, () -> scrap().reject("No", asker));
    }

    @Test void approvingThenCompleting() {
        EarthDisposalRequest r = scrap();
        assertTrue(r.isLive());
        assertThrows(IllegalStateException.class, () -> r.complete(boss));   // not approved yet
        r.approve("  Fine  ", boss);
        assertEquals("APPROVED", r.getStatus());
        assertEquals("Fine", r.getDecisionNote());
        assertEquals(boss, r.getDecidedBy());
        assertThrows(IllegalStateException.class, () -> r.approve(null, boss));   // only once
        r.complete(boss);
        assertEquals("COMPLETED", r.getStatus());
        assertFalse(r.isLive());
        assertThrows(IllegalStateException.class, () -> r.cancel("late", boss));
    }

    @Test void rejectingNeedsAReasonAndEndsTheRequest() {
        EarthDisposalRequest r = scrap();
        assertThrows(IllegalArgumentException.class, () -> r.reject(" ", boss));
        r.reject("Still serviceable", boss);
        assertEquals("REJECTED", r.getStatus());
        assertFalse(r.isLive());
        assertThrows(IllegalStateException.class, () -> r.complete(boss));
    }

    @Test void cancellingNeedsAReasonAndWorksOnlyWhileLive() {
        EarthDisposalRequest r = scrap();
        assertThrows(IllegalArgumentException.class, () -> r.cancel(null, asker));
        r.cancel("Buyer withdrew", asker);
        assertEquals("CANCELLED", r.getStatus());
        assertEquals(asker, r.getCancelledBy());
        assertThrows(IllegalStateException.class, () -> r.cancel("again", asker));
    }

    @Test void anApprovedRequestCanStillBeCancelled() {
        EarthDisposalRequest r = scrap();
        r.approve(null, boss);
        r.cancel("Plans changed", boss);
        assertEquals("CANCELLED", r.getStatus());
    }
}

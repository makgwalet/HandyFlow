package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.EarthFieldSubmission;
import za.co.handyflow.platform.earthmoving.domain.model.FieldRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FieldRulesTest {
    Instant now = Instant.parse("2026-10-20T12:00:00Z");
    TenantId tenant = TenantId.generate();
    UUID asset = UUID.randomUUID(), me = UUID.randomUUID(), boss = UUID.randomUUID();

    @Test void deploymentReleaseAndDisposalApprovalCanNeverBeQueued() {
        for (String k : new String[]{"DEPLOYMENT", "MAINTENANCE_RELEASE", "DISPOSAL_APPROVAL"})
            assertTrue(FieldRules.kindProblem(k).contains("fresh check"), k);
        assertNull(FieldRules.kindProblem("METER_READING"));
        assertNull(FieldRules.kindProblem("POSITION_READING"));
        assertNotNull(FieldRules.kindProblem("SELL_EVERYTHING"));
        assertEquals("METER_READING", FieldRules.kind(" meter_reading "));
    }

    @Test void entriesAreChecked() {
        assertNotNull(FieldRules.entryProblem("METER_READING", null, null, null, now, now));
        assertNotNull(FieldRules.entryProblem("METER_READING", new BigDecimal("-1"), null, null, now, now));
        assertNotNull(FieldRules.entryProblem("METER_READING", BigDecimal.TEN, null, null, null, now));
        assertNotNull(FieldRules.entryProblem("METER_READING", BigDecimal.TEN, null, null, now.plus(Duration.ofMinutes(6)), now));
        assertNull(FieldRules.entryProblem("METER_READING", BigDecimal.TEN, null, null, now.plus(Duration.ofMinutes(5)), now));
        assertNotNull(FieldRules.entryProblem("POSITION_READING", null, BigDecimal.ONE, null, now, now));
        assertNotNull(FieldRules.entryProblem("POSITION_READING", null, new BigDecimal("91"), BigDecimal.ONE, now, now));
        assertNull(FieldRules.entryProblem("POSITION_READING", null, new BigDecimal("-26.2"), new BigDecimal("28.0"), now.minus(Duration.ofDays(2)), now));
    }

    @Test void aReadingTakenBeforeANewerServerReadingIsAConflictNotAnOverwrite() {
        FieldRules.Verdict v = FieldRules.judgeMeter(new BigDecimal("1005"), now.minusSeconds(3600), new BigDecimal("1000"), now.minusSeconds(60));
        assertEquals("CONFLICT", v.status());
        assertTrue(v.reason().contains("Both are kept"));
    }

    @Test void aLowerReadingIsRefusedAndAHigherNewerOneIsApplied() {
        assertEquals("REJECTED", FieldRules.judgeMeter(new BigDecimal("990"), now, new BigDecimal("1000"), now.minusSeconds(60)).status());
        assertNull(FieldRules.judgeMeter(new BigDecimal("1000"), now, new BigDecimal("1000"), now.minusSeconds(60)));
        assertNull(FieldRules.judgeMeter(new BigDecimal("1010"), now, new BigDecimal("1000"), null));
    }

    @Test void conflictBeatsLowerWhenBothApply() {
        // Older than the server's latest AND lower: it is still kept for a person to look at, not silently dropped.
        assertEquals("CONFLICT", FieldRules.judgeMeter(new BigDecimal("900"), now.minusSeconds(3600), new BigDecimal("1000"), now.minusSeconds(60)).status());
    }

    @Test void aRefusedOrConflictingEntryNeedsAReason() {
        assertThrows(IllegalArgumentException.class, () -> EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", asset, now, BigDecimal.TEN, null, null, null, me, "REJECTED", " "));
        assertThrows(IllegalArgumentException.class, () -> EarthFieldSubmission.received(tenant, null, "METER_READING", asset, now, BigDecimal.TEN, null, null, null, me, "SYNCED", null));
        assertDoesNotThrow(() -> EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", asset, now, BigDecimal.TEN, null, null, null, me, "SYNCED", null));
    }

    @Test void onlyAnOpenConflictCanBeReviewedAndOnlyOnce() {
        EarthFieldSubmission synced = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", asset, now, BigDecimal.TEN, null, null, null, me, "SYNCED", null);
        assertThrows(IllegalStateException.class, () -> synced.review("KEPT_SERVER", null, boss));
        EarthFieldSubmission c = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", asset, now, BigDecimal.TEN, null, null, null, me, "CONFLICT", "Newer reading exists");
        assertTrue(c.isOpenConflict());
        assertThrows(IllegalArgumentException.class, () -> c.review("MAYBE", null, boss));
        c.review("kept_server", "  server is right ", boss);
        assertFalse(c.isOpenConflict());
        assertEquals("KEPT_SERVER", c.getReviewDecision());
        assertEquals("server is right", c.getReviewNote());
        assertEquals(boss, c.getReviewedBy());
        assertThrows(IllegalStateException.class, () -> c.review("APPLIED", null, boss));
    }
}

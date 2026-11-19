package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LicenceDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 6, 1);
    private static final String TXT = "A sufficiently long text for the rule";
    private static final List<Map<String, Object>> PROOF = List.of(Map.of("id", UUID.randomUUID().toString(), "fileName", "receipt.pdf"));

    private Licence licence(LocalDate expires) {
        return Licence.create(tenant, "LIC-1", LicenceType.LICENCE, "Operating licence", "Regulator", "N-1", null, today.minusYears(1), expires, "Alice", 90, null, null);
    }

    @Test @DisplayName("Create validates required fields, lead time and date order")
    void create() {
        assertThrows(IllegalArgumentException.class, () -> Licence.create(tenant, "L", null, "T", "A", null, null, null, null, "O", 90, null, null));
        assertThrows(IllegalArgumentException.class, () -> Licence.create(tenant, "L", LicenceType.PERMIT, " ", "A", null, null, null, null, "O", 90, null, null));
        assertThrows(IllegalArgumentException.class, () -> Licence.create(tenant, "L", LicenceType.PERMIT, "T", "A", null, null, null, null, "O", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> Licence.create(tenant, "L", LicenceType.PERMIT, "T", "A", null, null, null, null, "O", 366, null, null));
        assertThrows(IllegalArgumentException.class, () -> Licence.create(tenant, "L", LicenceType.PERMIT, "T", "A", null, null, today, today.minusDays(1), "O", 90, null, null));
        assertEquals(LicenceStatus.ACTIVE, licence(today.plusDays(100)).getStatus());
    }

    @Test @DisplayName("Renewal: start → submit needs proof → renewed needs a later expiry; the history records it and the licence is active again")
    void renewalFlow() {
        Licence l = licence(today.plusDays(30));
        assertThrows(IllegalStateException.class, () -> l.submitRenewal(today, "R1", PROOF, today), "must start first");
        l.startRenewal(today);
        assertEquals(LicenceStatus.RENEWAL_IN_PROGRESS, l.getStatus());
        assertThrows(IllegalArgumentException.class, () -> l.submitRenewal(today, "R1", List.of(), today));
        assertThrows(IllegalArgumentException.class, () -> l.submitRenewal(today.plusDays(1), "R1", PROOF, today));
        assertThrows(IllegalStateException.class, () -> l.recordRenewed(null, today.plusYears(1), null, "Alice", today), "submission first");
        l.submitRenewal(today, "R1", PROOF, today);
        assertThrows(IllegalArgumentException.class, () -> l.recordRenewed(null, today.plusDays(30), null, "Alice", today), "not later than the current expiry");
        l.recordRenewed(null, today.plusYears(1), "N-2", "Alice", today);
        assertEquals(LicenceStatus.ACTIVE, l.getStatus());
        assertEquals(today.plusYears(1), l.getExpiryDate());
        assertEquals("N-2", l.getLicenceNumber());
        assertEquals(1, l.getRenewalHistory().size());
        assertNull(l.getRenewalSubmittedOn());
    }

    @Test @DisplayName("Renewal can be started on an already-expired licence, and abandoning it needs a reason")
    void abandon() {
        Licence l = licence(today.minusDays(5));
        l.startRenewal(today);
        assertThrows(IllegalArgumentException.class, () -> l.abandonRenewal(" "));
        l.abandonRenewal("Decided not to renew this permit");
        assertEquals(LicenceStatus.ACTIVE, l.getStatus());
        assertThrows(IllegalStateException.class, () -> licence(null).startRenewal(today), "no expiry date, nothing to renew");
    }

    @Test @DisplayName("Expiry cannot be edited during a renewal; other fields can")
    void editDuringRenewal() {
        Licence l = licence(today.plusDays(30));
        l.startRenewal(today);
        assertThrows(IllegalStateException.class, () -> l.update(LicenceType.LICENCE, "T", "A", null, null, null, today.plusDays(60), "O", 90, null, null));
        l.update(LicenceType.LICENCE, "New title", "A", null, null, null, today.plusDays(30), "O", 90, null, null);
        assertEquals("New title", l.getTitle());
    }

    @Test @DisplayName("Suspend, reinstate, revoke and surrender need a reason; ended licences are final")
    void lifecycle() {
        Licence l = licence(today.plusDays(30));
        assertThrows(IllegalArgumentException.class, () -> l.suspend(""));
        l.suspend("Regulator inspection pending");
        assertEquals(LicenceStatus.SUSPENDED, l.getStatus());
        assertThrows(IllegalStateException.class, () -> l.startRenewal(today));
        assertThrows(IllegalArgumentException.class, () -> l.reinstate(null));
        l.reinstate("Inspection passed");
        assertEquals(LicenceStatus.ACTIVE, l.getStatus());
        l.revoke("Withdrawn by the regulator");
        assertTrue(l.isFinal());
        assertThrows(IllegalStateException.class, () -> l.surrender("Closing the business"));
        assertThrows(IllegalStateException.class, () -> l.update(LicenceType.LICENCE, "T", "A", null, null, null, null, "O", 90, null, null));
    }

    @Test @DisplayName("Condition: description length, recurring needs a first due date, frequency bounds")
    void conditionCreate() {
        UUID lid = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> LicenceCondition.add(tenant, lid, "short", null, null));
        assertThrows(IllegalArgumentException.class, () -> LicenceCondition.add(tenant, lid, TXT, null, 3));
        assertThrows(IllegalArgumentException.class, () -> LicenceCondition.add(tenant, lid, TXT, today, 0));
        assertThrows(IllegalArgumentException.class, () -> LicenceCondition.add(tenant, lid, TXT, today, 121));
        assertEquals(ConditionStatus.PENDING, LicenceCondition.add(tenant, lid, TXT, today, 12).getStatus());
    }

    @Test @DisplayName("Condition: one-off is met once; recurring returns to pending with the next due date")
    void conditionMet() {
        UUID lid = UUID.randomUUID();
        LicenceCondition once = LicenceCondition.add(tenant, lid, TXT, today, null);
        assertThrows(IllegalArgumentException.class, () -> once.markMet(today.plusDays(1), null, today, null));
        once.markMet(today, null, today, null);
        assertEquals(ConditionStatus.MET, once.getStatus());
        assertThrows(IllegalStateException.class, () -> once.markMet(today, null, today, null));

        LicenceCondition rec = LicenceCondition.add(tenant, lid, TXT, today, 6);
        assertThrows(IllegalArgumentException.class, () -> rec.markMet(today, null, today, null), "recurring needs the next due date");
        rec.markMet(today, "Report filed", today, today.plusMonths(6));
        assertEquals(ConditionStatus.PENDING, rec.getStatus());
        assertEquals(today.plusMonths(6), rec.getDueDate());
        assertEquals(today, rec.getLastMetOn());
    }

    @Test @DisplayName("Condition: breach needs a description, only from pending, and putting it right needs a note")
    void conditionBreach() {
        LicenceCondition c = LicenceCondition.add(tenant, UUID.randomUUID(), TXT, today, null);
        assertThrows(IllegalArgumentException.class, () -> c.markBreached("short"));
        c.markBreached("Report was not filed by the due date");
        assertEquals(ConditionStatus.BREACHED, c.getStatus());
        assertThrows(IllegalStateException.class, () -> c.markBreached("Report was not filed by the due date"));
        assertThrows(IllegalArgumentException.class, () -> c.markMet(today, "ok", today, null));
        c.markMet(today, "Report filed late and the regulator informed", today, null);
        assertEquals(ConditionStatus.MET, c.getStatus());
    }

    @Test @DisplayName("Snapshot needs a date and metrics")
    void snapshot() {
        assertThrows(IllegalArgumentException.class, () -> PostureSnapshot.take(tenant, null, SnapshotSource.MANUAL, "A", Map.of("a", 1L)));
        assertThrows(IllegalArgumentException.class, () -> PostureSnapshot.take(tenant, today, SnapshotSource.MANUAL, "A", Map.of()));
        PostureSnapshot s = PostureSnapshot.take(tenant, today, SnapshotSource.MONTHLY, "System", Map.of("a", 1L));
        assertEquals(today, s.getSnapshotDate());
    }
}

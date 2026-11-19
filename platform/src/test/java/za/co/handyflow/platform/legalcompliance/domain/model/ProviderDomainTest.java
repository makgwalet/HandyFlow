package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProviderDomainTest {

    private static final TenantId PROVIDER = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Set<AccessScope> BOTH = EnumSet.allOf(AccessScope.class);
    private static final Set<AccessScope> SUMMARY = EnumSet.of(AccessScope.SUMMARY);
    private final Instant now = Instant.now();

    private ProviderClient invited() {
        return ProviderClient.invite(PROVIDER, "CLI-00001", "Acme Advisory", "Northern Logistics", BOTH, null, null, ReviewCycle.QUARTERLY, null, "hash", now);
    }

    private ProviderClient active() {
        ProviderClient c = invited();
        c.accept(UUID.randomUUID(), SUMMARY, UUID.randomUUID(), "Client Admin", now, TODAY);
        return c;
    }

    @Test @DisplayName("Invitation: needs provider name, client name, a scope and sensible contract dates; starts INVITED with an expiry")
    void invite() {
        assertThrows(IllegalArgumentException.class, () -> ProviderClient.invite(PROVIDER, "R", " ", "C", BOTH, null, null, ReviewCycle.MONTHLY, null, "h", now), "name");
        assertThrows(IllegalArgumentException.class, () -> ProviderClient.invite(PROVIDER, "R", "P", " ", BOTH, null, null, ReviewCycle.MONTHLY, null, "h", now), "name");
        assertThrows(IllegalArgumentException.class, () -> ProviderClient.invite(PROVIDER, "R", "P", "C", Set.of(), null, null, ReviewCycle.MONTHLY, null, "h", now), "scope");
        assertThrows(IllegalArgumentException.class, () -> ProviderClient.invite(PROVIDER, "R", "P", "C", BOTH, TODAY, TODAY.minusDays(1), ReviewCycle.MONTHLY, null, "h", now), "dates");
        ProviderClient c = invited();
        assertEquals(ProviderClientStatus.INVITED, c.getStatus());
        assertNull(c.getClientTenantId());
        assertTrue(c.getInviteExpiresAt().isAfter(now.plus(13, ChronoUnit.DAYS)));
        assertEquals(BOTH, c.scopesRequested());
        assertTrue(c.scopesGranted().isEmpty());
    }

    @Test @DisplayName("Accept: grants only what was asked for, clears the code, links the client tenant and schedules the first review")
    void accept() {
        ProviderClient c = invited();
        UUID client = UUID.randomUUID();
        c.accept(client, SUMMARY, UUID.randomUUID(), "Client Admin", now, TODAY);
        assertEquals(ProviderClientStatus.ACTIVE, c.getStatus());
        assertEquals(client, c.getClientTenantId());
        assertEquals(SUMMARY, c.scopesGranted());
        assertNull(c.getInviteHash());
        assertEquals(LocalDate.of(2027, 2, 19), c.getNextReviewDue());
        assertThrows(IllegalStateException.class, () -> c.accept(client, SUMMARY, UUID.randomUUID(), "x", now, TODAY), "used");
    }

    @Test @DisplayName("Accept: refuses expired invitations, self-linking, empty grants and scopes that were not requested")
    void acceptRefusals() {
        ProviderClient expired = ProviderClient.invite(PROVIDER, "R", "P", "C", BOTH, null, null, ReviewCycle.MONTHLY, null, "h", now.minus(20, ChronoUnit.DAYS));
        assertThrows(IllegalStateException.class, () -> expired.accept(UUID.randomUUID(), SUMMARY, UUID.randomUUID(), "x", now, TODAY), "expired");
        ProviderClient c = invited();
        assertThrows(IllegalArgumentException.class, () -> c.accept(PROVIDER.getValue(), SUMMARY, UUID.randomUUID(), "x", now, TODAY), "self");
        assertThrows(IllegalArgumentException.class, () -> c.accept(UUID.randomUUID(), Set.of(), UUID.randomUUID(), "x", now, TODAY), "empty");
        ProviderClient narrow = ProviderClient.invite(PROVIDER, "R", "P", "C", SUMMARY, null, null, ReviewCycle.MONTHLY, null, "h", now);
        assertThrows(IllegalArgumentException.class, () -> narrow.accept(UUID.randomUUID(), BOTH, UUID.randomUUID(), "x", now, TODAY), "more than asked");
        assertEquals(ProviderClientStatus.INVITED, narrow.getStatus());
    }

    @Test @DisplayName("Reissue: only while unaccepted; replaces the hash and extends the expiry")
    void reissue() {
        ProviderClient c = invited();
        c.reissueInvite("new", SUMMARY, now.plus(3, ChronoUnit.DAYS));
        assertEquals("new", c.getInviteHash());
        assertEquals(SUMMARY, c.scopesRequested());
        ProviderClient a = active();
        assertThrows(IllegalStateException.class, () -> a.reissueInvite("x", SUMMARY, now));
    }

    @Test @DisplayName("Suspension: needs a reason; a client's suspension cannot be lifted by the provider, and vice versa")
    void suspension() {
        ProviderClient c = active();
        assertThrows(IllegalArgumentException.class, () -> c.suspend(AccessSide.CLIENT, "short"));
        c.suspend(AccessSide.CLIENT, "Reviewing our arrangements");
        assertEquals(ProviderClientStatus.SUSPENDED, c.getStatus());
        assertThrows(IllegalStateException.class, () -> c.resume(AccessSide.PROVIDER), "provider cannot lift client suspension");
        c.resume(AccessSide.CLIENT);
        assertEquals(ProviderClientStatus.ACTIVE, c.getStatus());
        assertNull(c.getSuspendedBy());
        c.suspend(AccessSide.PROVIDER, "Consultant on leave, pausing");
        assertThrows(IllegalStateException.class, () -> c.resume(AccessSide.CLIENT), "client cannot lift provider suspension");
        assertThrows(IllegalStateException.class, () -> invited().suspend(AccessSide.PROVIDER, "not accepted yet"));
    }

    @Test @DisplayName("Ending: final, needs a reason, clears the code; only the provider withdraws an invitation; either side can end accepted access")
    void ending() {
        ProviderClient pending = invited();
        assertThrows(IllegalStateException.class, () -> pending.end(AccessSide.CLIENT, "I do not want this", now));
        pending.end(AccessSide.PROVIDER, "Sent to the wrong organisation", now);
        assertEquals(ProviderClientStatus.ENDED, pending.getStatus());
        assertNull(pending.getInviteHash());
        ProviderClient a = active();
        assertThrows(IllegalArgumentException.class, () -> a.end(AccessSide.CLIENT, "no", now));
        a.end(AccessSide.CLIENT, "Contract not renewed", now);
        assertEquals(AccessSide.CLIENT, a.getEndedBy());
        assertThrows(IllegalStateException.class, () -> a.end(AccessSide.PROVIDER, "again please", now));
        assertThrows(IllegalStateException.class, () -> a.update("x", null, null, ReviewCycle.MONTHLY, null));
        assertThrows(IllegalStateException.class, () -> a.resume(AccessSide.CLIENT));
        // A client can end access while it is suspended.
        ProviderClient s = active();
        s.suspend(AccessSide.PROVIDER, "Pausing the engagement");
        s.end(AccessSide.CLIENT, "Moving to another firm", now);
        assertEquals(ProviderClientStatus.ENDED, s.getStatus());
    }

    @Test @DisplayName("Review scheduling follows the cycle and only applies to accepted relationships")
    void scheduling() {
        ProviderClient c = active();
        c.scheduleReview(LocalDate.of(2026, 11, 30));
        assertEquals(LocalDate.of(2027, 2, 28), c.getNextReviewDue());
        assertThrows(IllegalStateException.class, () -> invited().scheduleReview(TODAY));
    }

    @Test @DisplayName("Assignment: needs a person and a role; ends once with its date kept")
    void assignment() {
        UUID client = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ProviderAssignment.assign(PROVIDER, client, null, "A", AssignmentRole.LEAD, "M", TODAY));
        assertThrows(IllegalArgumentException.class, () -> ProviderAssignment.assign(PROVIDER, client, UUID.randomUUID(), " ", AssignmentRole.LEAD, "M", TODAY));
        assertThrows(IllegalArgumentException.class, () -> ProviderAssignment.assign(PROVIDER, client, UUID.randomUUID(), "A", null, "M", TODAY));
        ProviderAssignment a = ProviderAssignment.assign(PROVIDER, client, UUID.randomUUID(), "Thandi", AssignmentRole.LEAD, "Manager", TODAY);
        assertTrue(a.active());
        a.end(TODAY.plusDays(10));
        assertFalse(a.active());
        assertEquals(TODAY.plusDays(10), a.getEndedOn());
        assertThrows(IllegalStateException.class, () -> a.end(TODAY.plusDays(11)));
    }

    @Test @DisplayName("Review: needs notes, an outcome, a real reviewer and a date that is not in the future; keeps the counts it was given")
    void review() {
        UUID client = UUID.randomUUID(), me = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ProviderReview.record(PROVIDER, client, TODAY.plusDays(1), me, "T", ReviewOutcome.SATISFACTORY, "All registers current and reviewed", null, TODAY, TODAY), "future");
        assertThrows(IllegalArgumentException.class, () -> ProviderReview.record(PROVIDER, client, TODAY, me, "T", null, "All registers current and reviewed", null, TODAY, TODAY), "outcome");
        assertThrows(IllegalArgumentException.class, () -> ProviderReview.record(PROVIDER, client, TODAY, me, "T", ReviewOutcome.ESCALATED, "short", null, TODAY, TODAY), "notes");
        assertThrows(IllegalArgumentException.class, () -> ProviderReview.record(PROVIDER, client, TODAY, null, "T", ReviewOutcome.ESCALATED, "Enough words here for notes", null, TODAY, TODAY), "reviewer");
        ProviderReview r = ProviderReview.record(PROVIDER, client, TODAY, me, "Thandi", ReviewOutcome.ACTIONS_REQUIRED, "  Two licences expire inside 60 days  ", Map.of("licences.expiring", 2L), TODAY.plusMonths(3), TODAY);
        assertEquals("Two licences expire inside 60 days", r.getNotes());
        assertEquals(Long.valueOf(2L), r.getMetrics().get("licences.expiring"));
    }
}

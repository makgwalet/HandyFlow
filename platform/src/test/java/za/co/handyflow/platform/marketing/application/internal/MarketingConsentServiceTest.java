package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.marketing.domain.model.MktConsentEvidence;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.model.MktSuppressionEntry;
import za.co.handyflow.platform.marketing.domain.repository.MktConsentEvidenceRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktSuppressionEntryRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The single eligibility decision and the suppression / consent-evidence rules (POPIA, Section 69). */
class MarketingConsentServiceTest {

    MktSuppressionEntryRepository suppressions = mock(MktSuppressionEntryRepository.class);
    MktConsentEvidenceRepository  evidence     = mock(MktConsentEvidenceRepository.class);
    MarketingConsentService       service;

    final TenantId tenant = TenantId.of(UUID.randomUUID());
    final UUID     staff  = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new MarketingConsentService(suppressions, evidence); }

    private MktContactPreference pref(String email, boolean optedIn) {
        return MktContactPreference.create(tenant, "SUBSCRIBER", null, email, "Test", optedIn, "TEST");
    }

    // ── evaluate: fails closed ──────────────────────────────────────────────

    @Test void eligible_when_opted_in_and_not_suppressed() {
        var d = service.evaluate(pref("a@x.test", true), "a@x.test", Set.of());
        assertTrue(d.eligible());
        assertNull(d.reason());
    }

    @Test void missing_contact_is_never_eligible() {
        var d = service.evaluate(null, "a@x.test", Set.of());
        assertFalse(d.eligible());
        assertEquals(MarketingConsentService.BLOCKED_NO_CONTACT, d.reason());
    }

    @Test void not_opted_in_is_never_eligible() {
        var d = service.evaluate(pref("a@x.test", false), "a@x.test", Set.of());
        assertFalse(d.eligible());
        assertEquals(MarketingConsentService.BLOCKED_NOT_OPTED_IN, d.reason());
    }

    @Test void suppressed_address_is_blocked_even_if_the_contact_is_still_opted_in() {
        var d = service.evaluate(pref("a@x.test", true), "a@x.test", Set.of("a@x.test"));
        assertFalse(d.eligible());
        assertEquals(MarketingConsentService.BLOCKED_SUPPRESSED, d.reason());
    }

    @Test void suppression_match_is_case_and_whitespace_insensitive() {
        var d = service.evaluate(pref("a@x.test", true), "  A@X.Test ", Set.of("a@x.test"));
        assertFalse(d.eligible());
        assertEquals(MarketingConsentService.BLOCKED_SUPPRESSED, d.reason());
    }

    @Test void malformed_address_is_blocked() {
        assertFalse(service.evaluate(pref("a@x.test", true), "not-an-email", Set.of()).eligible());
        assertFalse(service.evaluate(pref("a@x.test", true), null, Set.of()).eligible());
    }

    @Test void null_suppression_set_does_not_throw() {
        assertTrue(service.evaluate(pref("a@x.test", true), "a@x.test", null).eligible());
    }

    // ── activeSuppressedAmong ───────────────────────────────────────────────

    @Test void suppressed_lookup_lowercases_and_batches_large_lists() {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 2500; i++) emails.add("User" + i + "@X.test");
        when(suppressions.findActiveEmailsAmong(eq(tenant), anyCollection())).thenReturn(List.of("user1@x.test"));

        Set<String> out = service.activeSuppressedAmong(tenant, emails);

        assertEquals(Set.of("user1@x.test"), out);
        @SuppressWarnings("unchecked") ArgumentCaptor<Collection<String>> cap = ArgumentCaptor.forClass(Collection.class);
        verify(suppressions, times(3)).findActiveEmailsAmong(eq(tenant), cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(c -> c.size() <= 1000), "each IN list must stay <= 1000");
        assertTrue(cap.getAllValues().get(0).iterator().next().equals("user0@x.test"), "addresses are lower-cased");
    }

    @Test void suppressed_lookup_with_no_emails_skips_the_database() {
        assertTrue(service.activeSuppressedAmong(tenant, List.of()).isEmpty());
        verifyNoInteractions(suppressions);
    }

    // ── suppress ────────────────────────────────────────────────────────────

    @Test void suppress_writes_evidence_only_when_a_new_row_was_created() {
        var existing = mock(MktSuppressionEntry.class);
        when(suppressions.insertIfAbsent(any(), eq(tenant.getValue()), eq("a@x.test"), eq("COMPLAINT"), any(), any(), any())).thenReturn(1);
        when(suppressions.findActive(tenant, "a@x.test")).thenReturn(Optional.of(existing));

        assertSame(existing, service.suppress(tenant, " A@X.test ", "COMPLAINT", "SES", "spam report", null));

        var cap = ArgumentCaptor.forClass(MktConsentEvidence.class);
        verify(evidence).save(cap.capture());
        assertEquals(MktConsentEvidence.SUPPRESSION_ADDED, cap.getValue().getEventType());
        assertEquals("a@x.test", cap.getValue().getEmail());
    }

    @Test void suppress_is_idempotent_and_writes_no_second_evidence_row() {
        var existing = mock(MktSuppressionEntry.class);
        when(suppressions.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);   // lost the race
        when(suppressions.findActive(tenant, "a@x.test")).thenReturn(Optional.of(existing));

        assertSame(existing, service.suppress(tenant, "a@x.test", "UNSUBSCRIBED", "LINK", null, null));
        verifyNoInteractions(evidence);
    }

    @Test void suppress_rejects_bad_address_and_unknown_reason() {
        var e1 = assertThrows(HandyFlowException.class, () -> service.suppress(tenant, "nope", "MANUAL", "S", null, null));
        assertEquals(HttpStatus.BAD_REQUEST, e1.getStatus());
        var e2 = assertThrows(HandyFlowException.class, () -> service.suppress(tenant, "a@x.test", "BECAUSE", "S", null, null));
        assertEquals(HttpStatus.BAD_REQUEST, e2.getStatus());
        verifyNoInteractions(evidence);
    }

    // ── lift ────────────────────────────────────────────────────────────────

    @Test void lift_requires_a_documented_reason() {
        var ex = assertThrows(HandyFlowException.class, () -> service.lift(tenant, UUID.randomUUID(), "too short", staff));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(suppressions);
    }

    @Test void lift_is_tenant_scoped_a_foreign_id_is_not_found() {
        UUID id = UUID.randomUUID();
        when(suppressions.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.lift(tenant, id, "customer asked in writing", staff));
        verify(suppressions, never()).save(any());
    }

    @Test void lifting_twice_is_a_conflict() {
        UUID id = UUID.randomUUID();
        var entry = mock(MktSuppressionEntry.class);
        when(entry.isActive()).thenReturn(false);
        when(suppressions.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(entry));
        var ex = assertThrows(HandyFlowException.class, () -> service.lift(tenant, id, "customer asked in writing", staff));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test void lift_records_who_and_why_and_does_not_reopt_in() {
        UUID id = UUID.randomUUID();
        var entry = mock(MktSuppressionEntry.class);
        when(entry.isActive()).thenReturn(true);
        when(entry.getEmail()).thenReturn("a@x.test");
        when(suppressions.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(entry));

        service.lift(tenant, id, "  customer asked in writing  ", staff);

        verify(entry).lift(staff, "customer asked in writing");
        verify(suppressions).save(entry);
        var cap = ArgumentCaptor.forClass(MktConsentEvidence.class);
        verify(evidence).save(cap.capture());
        assertEquals(MktConsentEvidence.SUPPRESSION_LIFTED, cap.getValue().getEventType());
        verifyNoMoreInteractions(evidence);
    }

    // ── consent evidence ────────────────────────────────────────────────────

    @Test void opt_in_import_and_opt_out_each_append_one_evidence_row() {
        var p = pref("a@x.test", true);
        service.recordOptIn(tenant, p, "FORM", "ticked the box", staff);
        service.recordImportDeclaration(tenant, p, "IMPORT", staff);
        service.recordOptOut(tenant, p, "EMAIL_UNSUBSCRIBE_LINK");

        var cap = ArgumentCaptor.forClass(MktConsentEvidence.class);
        verify(evidence, times(3)).save(cap.capture());
        assertEquals(List.of(MktConsentEvidence.OPT_IN, MktConsentEvidence.IMPORT_DECLARATION, MktConsentEvidence.OPT_OUT),
                cap.getAllValues().stream().map(MktConsentEvidence::getEventType).toList());
    }

    @Test void evidence_text_is_capped() {
        service.recordOptIn(tenant, pref("a@x.test", true), "FORM", "x".repeat(5000), staff);
        var cap = ArgumentCaptor.forClass(MktConsentEvidence.class);
        verify(evidence).save(cap.capture());
        assertEquals(2000, cap.getValue().getEvidenceText().length());
    }
}

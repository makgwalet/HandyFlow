package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.clinic.ClaimMoneyEvent;
import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;
import za.co.handyflow.platform.clinic.application.internal.ClinicClaimLedgerService.OpenClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaimLine;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.AllocateRequest;
import za.co.handyflow.platform.clinic.dto.billing.ClaimMoneyDtos.ShareRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicClaimMoneyServiceTest {

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT = TenantId.of(TENANT_UUID);

    @Mock ClinicClaimRepository claimRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock ClinicClaimLedgerService ledger;
    @Mock ApplicationEventPublisher events;
    @Mock AccountingFacade accounting;
    ClinicClaimMoneyService service;
    final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClinicClaimMoneyService(claimRepo, consultationRepo, ledger, events, accounting);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.toString(), "n/a", List.of()));
        lenient().when(claimRepo.save(any(ClinicClaim.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    /** A scheme claim worth R1000 in the given status. */
    ClinicClaim claim(String status) {
        ClinicClaim c = ClinicClaim.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, "Discovery", "DH1", "00");
        c.addLine(ClinicClaimLine.of(c.getId(), "CONSULTATION", "0191", null, "I10", "Consultation", BigDecimal.ONE, new BigDecimal("1000.00"), null, 0));
        c.expectSchemeToCover();
        c.submit("REF");
        if (!"SUBMITTED".equals(status)) c.applyLedgerStatus(status);
        return c;
    }

    void ledgerHolds(ClinicClaim c, String paid, String wo, String cr) {
        when(claimRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(ledger.totals(TENANT_UUID, c.getId())).thenReturn(new Totals(new BigDecimal(paid), new BigDecimal(wo), new BigDecimal(cr)));
    }

    @Test void markPaidReceivesTheRemainingBalance() {
        ClinicClaim c = claim("PARTIAL");
        when(claimRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));
        // before the insert R400 is paid; after it the ledger reads fully paid
        when(ledger.totals(TENANT_UUID, c.getId())).thenReturn(new Totals(new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                .thenReturn(new Totals(new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO));

        service.markPaid(TENANT, c.getId(), "REM-1");

        verify(ledger).addPayment(eq(TENANT_UUID), eq(c.getId()), eq(new BigDecimal("600.00")), any(), eq("REM-1"), eq(null), eq("SINGLE"), eq(null), eq(user));
        assertThat(c.getStatus()).isEqualTo("PAID");
        ArgumentCaptor<ClaimMoneyEvent> ev = ArgumentCaptor.forClass(ClaimMoneyEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(ClaimMoneyEvent.SCHEME_PAYMENT_RECEIVED);
        assertThat(ev.getValue().amount()).isEqualByComparingTo("600.00");
    }

    @Test void theClaimsOwnAmountsAreNotRewrittenByAPayment() {
        ClinicClaim c = claim("ACCEPTED");
        ledgerHolds(c, "0", "0", "0");
        service.partial(TENANT, c.getId(), new BigDecimal("300"), null);
        assertThat(c.getSchemePortion()).isEqualByComparingTo("1000.00");
        assertThat(c.getPatientPortion()).isEqualByComparingTo("0");
    }

    @Test void writeOffNeedsAReasonAndAKnownPerson() {
        ClinicClaim c = claim("PARTIAL");
        assertThatThrownBy(() -> service.writeOff(TENANT, c.getId(), new BigDecimal("50"), "short"))
                .isInstanceOf(IllegalArgumentException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.writeOff(TENANT, c.getId(), new BigDecimal("50"), "Scheme short-paid the tariff"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Sign in again");
        verify(ledger, never()).addAdjustment(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void writeOffRecordsTheAuthoriserAndPublishesAnEvent() {
        ClinicClaim c = claim("PARTIAL");
        ledgerHolds(c, "700.00", "0", "0");
        service.writeOff(TENANT, c.getId(), new BigDecimal("300"), "  Scheme short-paid the tariff ");
        verify(ledger).addAdjustment(eq(TENANT_UUID), eq(c.getId()), eq("WRITE_OFF"), eq(new BigDecimal("300.00")), eq("Scheme short-paid the tariff"), eq(user), any());
        ArgumentCaptor<ClaimMoneyEvent> ev = ArgumentCaptor.forClass(ClaimMoneyEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(ClaimMoneyEvent.CLAIM_WRITTEN_OFF);
    }

    @Test void writeOffBeyondTheBalanceIsRefusedAndNothingIsRecorded() {
        ClinicClaim c = claim("PARTIAL");
        ledgerHolds(c, "700.00", "0", "0");
        assertThatThrownBy(() -> service.writeOff(TENANT, c.getId(), new BigDecimal("300.01"), "Scheme short-paid the tariff"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ledger, never()).addAdjustment(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test void aClaimWithMoneyOnItCannotBeVoided() {
        ClinicClaim c = claim("PARTIAL");
        ledgerHolds(c, "100.00", "0", "0");
        assertThatThrownBy(() -> service.voidClaim(TENANT, c.getId(), "Wrong member number entered"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("credit note");
        assertThat(c.getStatus()).isEqualTo("PARTIAL");
    }

    @Test void voidingFreesTheConsultationAndPublishesAnEvent() {
        ClinicClaim c = claim("SUBMITTED");
        ledgerHolds(c, "0", "0", "0");
        ClinicConsultation consult = ClinicConsultation.create(TENANT, c.getPatientId(), null, null, "Check");
        consult.markBilled("0191", new BigDecimal("1000"));
        when(consultationRepo.findById(c.getConsultationId())).thenReturn(Optional.of(consult));

        service.voidClaim(TENANT, c.getId(), "Wrong member number entered");

        assertThat(c.getStatus()).isEqualTo("VOIDED");
        assertThat(c.getVoidReason()).isEqualTo("Wrong member number entered");
        assertThat(c.getVoidedBy()).isEqualTo(user);
        assertThat(consult.isBilled()).isFalse();
        ArgumentCaptor<ClaimMoneyEvent> ev = ArgumentCaptor.forClass(ClaimMoneyEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(ClaimMoneyEvent.CLAIM_VOIDED);
    }

    @Test void creditNoteOnARejectedClaimClosesItWithoutTouchingThePatient() {
        ClinicClaim c = claim("REJECTED");
        when(claimRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(ledger.totals(TENANT_UUID, c.getId())).thenReturn(Totals.NONE)
                .thenReturn(new Totals(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00")));
        when(ledger.addAdjustment(any(), any(), eq("CREDIT_NOTE"), any(), any(), any(), any())).thenReturn("CN000001");

        service.creditNote(TENANT, c.getId(), new BigDecimal("1000"), "Scheme rejected; resubmission not possible");

        assertThat(c.getStatus()).isEqualTo("CLOSED");
        assertThat(c.getPatientPortion()).isEqualByComparingTo("0");
        ArgumentCaptor<ClaimMoneyEvent> ev = ArgumentCaptor.forClass(ClaimMoneyEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().reference()).isEqualTo("CN000001");
    }

    // ── allocation ───────────────────────────────────────────────────────────────────────────────────────────────────

    OpenClaim open(UUID id, long day, String portion) {
        return new OpenClaim(id, "ACCEPTED", "Patient " + day, "REF" + day, Instant.ofEpochSecond(day * 86400), new BigDecimal(portion), Totals.NONE);
    }

    @Test void previewRecordsNothing() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(ledger.openForScheme(TENANT_UUID, "Discovery")).thenReturn(List.of(open(b, 2, "500"), open(a, 1, "800")));

        var r = service.allocate(TENANT, new AllocateRequest("Discovery", new BigDecimal("1000"), null, "REM", null, null, true));

        assertThat(r.recorded()).isFalse();
        assertThat(r.method()).isEqualTo("OLDEST_FIRST");
        assertThat(r.shares()).hasSize(2);
        assertThat(r.shares().get(0).claimId()).isEqualTo(a);
        assertThat(r.shares().get(0).amount()).isEqualByComparingTo("800.00");
        assertThat(r.shares().get(1).amount()).isEqualByComparingTo("200.00");
        verify(ledger, never()).addPayment(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void recordingWritesOneRowPerClaimUnderOneBatch() {
        ClinicClaim a = claim("ACCEPTED"), b = claim("ACCEPTED");
        when(ledger.openForScheme(TENANT_UUID, "Discovery")).thenReturn(List.of(open(a.getId(), 1, "1000"), open(b.getId(), 2, "1000")));
        when(claimRepo.findActiveById(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(claimRepo.findActiveById(TENANT, b.getId())).thenReturn(Optional.of(b));
        when(ledger.totals(any(), any())).thenReturn(Totals.NONE);

        var r = service.allocate(TENANT, new AllocateRequest("Discovery", new BigDecimal("1200"), null, "REM", null, null, false));

        assertThat(r.recorded()).isTrue();
        verify(ledger).addPayment(eq(TENANT_UUID), eq(a.getId()), eq(new BigDecimal("1000.00")), any(), eq("REM"), eq(r.batchId()), eq("OLDEST_FIRST"), eq(null), eq(user));
        verify(ledger).addPayment(eq(TENANT_UUID), eq(b.getId()), eq(new BigDecimal("200.00")), any(), eq("REM"), eq(r.batchId()), eq("OLDEST_FIRST"), eq(null), eq(user));
        verify(events, times(2)).publishEvent(any(ClaimMoneyEvent.class));
    }

    @Test void aManualSplitWithoutAReasonIsRefused() {
        UUID a = UUID.randomUUID();
        when(ledger.openForScheme(TENANT_UUID, "Discovery")).thenReturn(List.of(open(a, 1, "1000")));
        assertThatThrownBy(() -> service.allocate(TENANT, new AllocateRequest("Discovery", new BigDecimal("300"), null, "REM",
                List.of(new ShareRequest(a, new BigDecimal("300"))), "", false))).isInstanceOf(IllegalArgumentException.class);
        verify(ledger, never()).addPayment(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void aBalanceThatChangedWhileAllocatingStopsTheWholeBatch() {
        ClinicClaim a = claim("ACCEPTED");
        when(ledger.openForScheme(TENANT_UUID, "Discovery")).thenReturn(List.of(open(a.getId(), 1, "1000")));
        when(claimRepo.findActiveById(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(ledger.totals(any(), any())).thenReturn(new Totals(new BigDecimal("900.00"), BigDecimal.ZERO, BigDecimal.ZERO));

        assertThatThrownBy(() -> service.allocate(TENANT, new AllocateRequest("Discovery", new BigDecimal("500"), null, null, null, null, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed");
        verify(ledger, never()).addPayment(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void aSchemeNameIsRequired() {
        assertThatThrownBy(() -> service.allocate(TENANT, new AllocateRequest(" ", new BigDecimal("1"), null, null, null, null, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

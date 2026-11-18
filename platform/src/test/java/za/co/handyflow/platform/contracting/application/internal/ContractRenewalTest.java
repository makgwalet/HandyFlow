package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractRenewalDecision;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractObligationDtos.RenewalDecisionRequest;
import za.co.handyflow.platform.contracting.dto.ContractObligationDtos.RenewalDecisionResponse;
import za.co.handyflow.platform.contracting.dto.ContractObligationDtos.RenewalItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractRenewalTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock ContractObligationRepository obligationRepo;
    @Mock ContractRenewalDecisionRepository decisionRepo;
    @Mock ContractNumberGenerator numberGen;
    @InjectMocks ContractObligationService service;

    TenantId tenant = TenantId.generate();
    Contract signed;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        signed = Contract.create(tenant, "C-1", "Lease", "LEASE", "<p>body</p>", null, UUID.randomUUID());
        signed.setDates(today.minusDays(300), today.plusDays(60));
        signed.setAutoRenew(true, 30);
        signed.markSigned();
        lenient().when(contractRepo.findActiveById(tenant, signed.getId())).thenReturn(Optional.of(signed));
        lenient().when(numberGen.next(tenant)).thenReturn("C-2");
    }

    @Test
    void noticeDeadlineIsEndDateMinusNoticeDaysAndStateReflectsIt() {
        RenewalItem item = ContractObligationService.renewalItem(signed, null, today);
        assertEquals(today.plusDays(30), item.noticeDeadline());
        assertEquals("DECISION_NEEDED", item.state());
        Contract longNotice = Contract.create(tenant, "C-3", "Lease", "LEASE", "<p>b</p>", null, null);
        longNotice.setDates(today.minusDays(300), today.plusDays(60));
        longNotice.setAutoRenew(true, 90);   // deadline was 30 days ago
        longNotice.markSigned();
        assertEquals("NOTICE_PASSED", ContractObligationService.renewalItem(longNotice, null, today).state());
        ContractRenewalDecision d = ContractRenewalDecision.record(tenant.getValue(), signed.getId(), "DO_NOT_RENEW", null, null, null);
        assertEquals("DECIDED", ContractObligationService.renewalItem(signed, d, today).state());
    }

    @Test
    void renewCreatesASuccessorDraftWithCopiedPartiesAndLinksIt() {
        ContractParty p = ContractParty.create(tenant, signed.getId(), "COMPANY", "CLIENT", "Pat", "pat@x.co", null, "Pat Co", 1);
        when(partyRepo.findByContract(signed.getId())).thenReturn(List.of(p));
        when(decisionRepo.findByTenantIdAndContractIdOrderByDecidedAtDesc(tenant.getValue(), signed.getId())).thenReturn(List.of());

        RenewalDecisionResponse r = service.decide(tenant, signed.getId(),
                new RenewalDecisionRequest("renew", "good supplier", null, today.plusDays(425)), UUID.randomUUID());

        ArgumentCaptor<Contract> cap = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepo).save(cap.capture());
        Contract next = cap.getValue();
        assertEquals("DRAFT", next.getStatus());
        assertEquals(signed.getId(), next.getRenewedFromContractId());
        assertEquals(signed.getEndDate().plusDays(1), next.getStartDate());
        assertEquals(next.getId(), r.successorContractId());
        ArgumentCaptor<ContractParty> party = ArgumentCaptor.forClass(ContractParty.class);
        verify(partyRepo).save(party.capture());
        assertEquals("PENDING", party.getValue().getSigningStatus());
        assertEquals(next.getId(), party.getValue().getContractId());
        assertEquals("SIGNED", signed.getStatus());   // the original is untouched
    }

    @Test
    void secondRenewCannotCreateASecondSuccessor() {
        ContractRenewalDecision earlier = ContractRenewalDecision.record(tenant.getValue(), signed.getId(), "RENEW", null, null, UUID.randomUUID());
        when(decisionRepo.findByTenantIdAndContractIdOrderByDecidedAtDesc(tenant.getValue(), signed.getId())).thenReturn(List.of(earlier));
        assertThrows(IllegalStateException.class, () -> service.decide(tenant, signed.getId(),
                new RenewalDecisionRequest("RENEW", null, null, today.plusDays(400)), null));
        verify(contractRepo, never()).save(any());
    }

    @Test
    void renewNeedsANewEndDateAfterTheStart() {
        when(decisionRepo.findByTenantIdAndContractIdOrderByDecidedAtDesc(any(), any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.decide(tenant, signed.getId(), new RenewalDecisionRequest("RENEW", null, null, null), null));
        assertThrows(IllegalArgumentException.class, () -> service.decide(tenant, signed.getId(),
                new RenewalDecisionRequest("RENEW", null, null, today.plusDays(10)), null));
    }

    @Test
    void doNotRenewJustRecordsTheDecision() {
        RenewalDecisionResponse r = service.decide(tenant, signed.getId(), new RenewalDecisionRequest("DO_NOT_RENEW", "moving premises", null, null), null);
        assertEquals("DO_NOT_RENEW", r.decision());
        assertNull(r.successorContractId());
        verify(contractRepo, never()).save(any());
    }

    @Test
    void onlySignedContractsTakeADecision() {
        Contract draft = Contract.create(tenant, "C-9", "x", "LEASE", "<p>b</p>", null, null);
        when(contractRepo.findActiveById(tenant, draft.getId())).thenReturn(Optional.of(draft));
        assertThrows(IllegalStateException.class, () -> service.decide(tenant, draft.getId(), new RenewalDecisionRequest("DO_NOT_RENEW", null, null, null), null));
    }

    @Test
    void anAutoRenewingContractPastItsEndDateStaysOnTheListAsPastEndUntilDecided() {
        Contract past = Contract.create(tenant, "C-9", "Old lease", "LEASE", "<p>b</p>", null, null);
        past.setDates(today.minusDays(400), today.minusDays(10));
        past.setAutoRenew(true, 30);
        past.markSigned();
        org.mockito.Mockito.when(contractRepo.findSignedExpiringWithin(org.mockito.ArgumentMatchers.eq(tenant), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        org.mockito.Mockito.when(contractRepo.findSignedAutoRenewPastEnd(org.mockito.ArgumentMatchers.eq(tenant), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(past));
        org.mockito.Mockito.when(decisionRepo.findByContracts(org.mockito.ArgumentMatchers.eq(tenant.getValue()), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        List<RenewalItem> items = service.renewals(tenant, 90);
        assertEquals(1, items.size());
        assertEquals("PAST_END", items.get(0).state());
        assertNull(items.get(0).latestDecision());
    }
}

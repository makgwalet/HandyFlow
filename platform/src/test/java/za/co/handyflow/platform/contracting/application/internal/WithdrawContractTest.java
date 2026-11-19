package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Withdrawing a contract that was sent for signing: links revoked, codes discarded, evidence kept, who/when/why recorded, signers told,
 * and no further signing. A fully signed contract is not withdrawn. A new draft keeps the withdrawn record untouched.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawContractTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock ContractSigningTokenRepository signingTokenRepo;
    @Mock ContractWithdrawalRepository withdrawalRepo;
    @Mock ContractCommentRepository commentRepo;
    @Mock ContractNumberGenerator numberGen;
    @Mock ContractRedlineService redline;
    @Mock OtpService otpService;
    @Mock ContractApprovalService approvals;
    @Mock EmailService emailService;
    @Mock SigningPolicy signingPolicy;
    @InjectMocks ContractingService service;

    TenantId tenant = TenantId.generate();
    Contract contract;
    ContractParty alice, bob;

    @BeforeEach
    void setUp() {
        contract = Contract.create(tenant, "CON-1", "Supply agreement", "SERVICE", "<p>Body</p>", null, UUID.randomUUID());
        contract.send();
        alice = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "CLIENT", "Alice", "alice@example.com", "0820000001", null, 1);
        bob = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "SUPPLIER", "Bob", "bob@example.com", "0820000002", null, 2);
        lenient().when(contractRepo.findForUpdate(tenant.getValue(), contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(partyRepo.findByContract(contract.getId())).thenReturn(List.of(alice, bob));
        lenient().when(commentRepo.findByContract(any())).thenReturn(List.of());
    }

    private ContractSigningToken token(ContractParty p) {
        return ContractSigningToken.create(tenant.getValue(), contract.getId(), p.getId(), "raw-" + p.getId(), java.time.Instant.now().plusSeconds(3600), null);
    }

    @Test
    void withdrawingRevokesEveryLinkDiscardsCodesAndRecordsTheWithdrawal() {
        ContractSigningToken ta = token(alice), tb = token(bob);
        when(signingTokenRepo.findAllActiveByPartyId(alice.getId())).thenReturn(List.of(ta));
        when(signingTokenRepo.findAllActiveByPartyId(bob.getId())).thenReturn(List.of(tb));
        when(otpService.invalidate(alice.getId().toString())).thenReturn(1);
        when(otpService.invalidate(bob.getId().toString())).thenReturn(0);

        ContractResponse r = service.withdraw(tenant, contract.getId(), "Wrong pricing schedule attached");

        assertEquals("WITHDRAWN", r.status());
        assertNotNull(ta.getRevokedAt());
        assertNotNull(tb.getRevokedAt());
        assertFalse(ta.isValid());
        ArgumentCaptor<ContractWithdrawal> w = ArgumentCaptor.forClass(ContractWithdrawal.class);
        verify(withdrawalRepo).save(w.capture());
        assertEquals("Wrong pricing schedule attached", w.getValue().getReason());
        assertEquals(2, w.getValue().getLinksRevoked());
        assertEquals(1, w.getValue().getOtpChallengesCleared());
        assertEquals(2, w.getValue().getPartiesTotal());
        verify(approvals).assertApproved(tenant, ApprovalAction.WITHDRAW, contract.getId());
        verify(approvals).consume(eq(tenant), eq(ApprovalAction.WITHDRAW), eq(contract.getId()), any());
        verify(emailService).send(eq("alice@example.com"), contains("withdrawn"), anyString());
        verify(emailService).send(eq("bob@example.com"), contains("withdrawn"), anyString());
    }

    @Test
    void signaturesAlreadyGivenStayAndTheContractIsNotTreatedAsExecuted() {
        alice.markSigned("10.0.0.1", "agent");
        service.withdraw(tenant, contract.getId(), "Wrong pricing schedule attached");
        assertEquals("SIGNED", alice.getSigningStatus(), "a recorded signature is never removed");
        assertEquals("WITHDRAWN", contract.getStatus());
        assertNull(contract.getSignedAt());
        assertFalse(contract.isExecuted());
        verify(partyRepo, never()).delete(any());
        verify(withdrawalRepo).save(argThat(w -> w.getPartiesSigned() == 1 && w.getPartiesTotal() == 2));
    }

    @Test
    void aReasonIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(tenant, contract.getId(), "  short "));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(tenant, contract.getId(), null));
        verifyNoInteractions(withdrawalRepo);
        assertEquals("SENT", contract.getStatus());
    }

    @Test
    void withoutTheRequiredApprovalNothingChanges() {
        doThrow(new IllegalStateException("This needs a second person's approval first. Request approval, then try again."))
                .when(approvals).assertApproved(tenant, ApprovalAction.WITHDRAW, contract.getId());
        assertThrows(IllegalStateException.class, () -> service.withdraw(tenant, contract.getId(), "Wrong pricing schedule attached"));
        assertEquals("SENT", contract.getStatus());
        verifyNoInteractions(withdrawalRepo, otpService, signingTokenRepo, emailService);
    }

    @Test
    void aFullySignedContractCannotBeWithdrawn() {
        contract.markSigned();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.withdraw(tenant, contract.getId(), "Wrong pricing schedule attached"));
        assertTrue(e.getMessage().contains("termination or an amendment"));
        verifyNoInteractions(withdrawalRepo, emailService);
    }

    @Test
    void aDraftOrAlreadyWithdrawnContractCannotBeWithdrawn() {
        Contract draft = Contract.create(tenant, "CON-2", "T", "NDA", "<p>x</p>", null, null);
        assertThrows(IllegalStateException.class, draft::withdraw);
        contract.withdraw();
        assertThrows(IllegalStateException.class, contract::withdraw);
    }

    // ── no further signing on a withdrawn contract ──

    @Test
    void noOneCanSignOnAWithdrawnContract() {
        contract.withdraw();
        assertThrows(IllegalStateException.class, () -> service.signContract(tenant, contract.getId(), alice.getId(),
                new za.co.handyflow.platform.contracting.dto.SignContractRequest("123456", null), "10.0.0.1", "agent", UUID.randomUUID()));
        verifyNoInteractions(otpService);
    }

    @Test
    void aWithdrawnContractCannotBeDeclinedOrIssueCodes() {
        contract.withdraw();
        assertThrows(IllegalStateException.class, () -> service.declineByToken(tenant, contract.getId(), alice.getId(), "no"));
        assertThrows(IllegalStateException.class, () -> service.requestOtp(tenant, contract.getId(), alice.getId()));
        assertThrows(IllegalStateException.class, () -> service.requestOtpByToken(tenant, contract.getId(), alice.getId()));
        assertThrows(IllegalStateException.class, () -> service.resendSigningLink(tenant, contract.getId(), alice.getId()));
        verifyNoInteractions(otpService);
    }

    // ── new draft from a withdrawn version ──

    @Test
    void aNewDraftCarriesTheTextForwardAndLeavesTheWithdrawnRecordAlone() {
        contract.lockBody("abc123");
        contract.withdraw();
        when(contractRepo.findRevisionOf(tenant, contract.getId())).thenReturn(Optional.empty());
        when(numberGen.next(tenant)).thenReturn("CON-9");

        ContractResponse r = service.reviseWithdrawn(tenant, contract.getId());

        assertEquals("DRAFT", r.status());
        assertEquals("CON-9", r.contractNumber());
        assertNotEquals(contract.getId(), r.id());
        assertEquals("WITHDRAWN", contract.getStatus(), "the withdrawn record is not changed");
        assertEquals("abc123", contract.getBodyHash());
        assertNull(r.bodyHash(), "the draft has no locked hash, so nothing carries over as already sent");
        assertEquals(2, r.parties().size());
        assertTrue(r.parties().stream().allMatch(p -> "PENDING".equals(p.signingStatus())), "every party starts unsigned");
        verify(redline).snapshot(eq(tenant), any(Contract.class), eq("REVISED_FROM_WITHDRAWN"), anyString(), any());
        verifyNoInteractions(signingTokenRepo, emailService);
    }

    @Test
    void onlyOneNewDraftCanBeMadeAndOnlyFromAWithdrawnContract() {
        assertThrows(IllegalStateException.class, () -> service.reviseWithdrawn(tenant, contract.getId()), "still SENT");
        contract.withdraw();
        when(contractRepo.findRevisionOf(tenant, contract.getId())).thenReturn(Optional.of(mock(Contract.class)));
        assertThrows(IllegalStateException.class, () -> service.reviseWithdrawn(tenant, contract.getId()));
        verify(contractRepo, never()).save(argThat(c -> "DRAFT".equals(c.getStatus())));
    }

    @Test
    void theNewDraftRecordsWhichWithdrawnContractItCameFrom() {
        contract.withdraw();
        Contract draft = Contract.reviseFrom(contract, "CON-9", null);
        assertEquals(contract.getId(), draft.getRevisionOfContractId());
        assertEquals(2, draft.getRevisionNo());
        assertThrows(IllegalStateException.class, () -> Contract.reviseFrom(draft, "CON-10", null), "a draft is not a withdrawn contract");
    }
}

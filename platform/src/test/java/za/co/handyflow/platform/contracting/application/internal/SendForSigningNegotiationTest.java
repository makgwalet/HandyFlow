package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractSigningTokenRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression cover for sending a contract for signing now that negotiation can hold it back: the send reads the contract under the
 * row lock, runs every check before anything changes, and a blocked send leaves no trace (no hash, no status change, no tokens, no
 * emails, no frozen negotiation). The happy path is covered by the end-to-end checks that need a database.
 */
@ExtendWith(MockitoExtension.class)
class SendForSigningNegotiationTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock ContractSigningTokenRepository signingTokenRepo;
    @Mock SigningPolicy signingPolicy;
    @Mock ContractVariableResolver variableResolver;
    @Mock ContractRiskService riskService;
    @Mock ContractNegotiationService negotiationService;
    @Mock EmailService emailService;
    @InjectMocks ContractingService service;

    TenantId tenant = TenantId.generate();
    UUID id = UUID.randomUUID();
    Contract contract = mock(Contract.class);
    ContractParty party = mock(ContractParty.class);

    @BeforeEach
    void setUp() {
        lenient().when(contractRepo.findForUpdate(tenant.getValue(), id)).thenReturn(Optional.of(contract));
        lenient().when(contract.getBody()).thenReturn("<p>Final wording.</p>");
        lenient().when(partyRepo.findByContract(id)).thenReturn(List.of(party));
        lenient().when(variableResolver.findUnresolved(anyString())).thenReturn(List.of());
    }

    private void assertNothingChanged() {
        verify(contract, never()).lockBody(any());
        verify(contract, never()).send();
        verify(contractRepo, never()).save(any());
        verify(negotiationService, never()).freezeOnSend(any(), any(), any());
        verifyNoInteractions(signingTokenRepo, emailService);
    }

    @Test
    void aNegotiationThatIsStillOpenBlocksTheSendAndChangesNothing() {
        doThrow(new IllegalStateException("2 proposal(s) still need a decision")).when(negotiationService).assertReadyForSigning(tenant, contract);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        assertTrue(e.getMessage().contains("still need a decision"));
        assertNothingChanged();
    }

    @Test
    void theContractIsReadUnderTheRowLockNeverWithAPlainRead() {
        doThrow(new IllegalStateException("blocked")).when(negotiationService).assertReadyForSigning(tenant, contract);
        assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        verify(contractRepo).findForUpdate(tenant.getValue(), id);
        verify(contractRepo, never()).findActiveById(any(), any());
    }

    @Test
    void aMissingOrForeignContractIsNotFoundAndNothingElseIsChecked() {
        TenantId other = TenantId.generate();
        when(contractRepo.findForUpdate(other.getValue(), id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.sendForSigning(other, id));
        verifyNoInteractions(negotiationService, riskService, signingPolicy);
        assertNothingChanged();
    }

    @Test
    void theChecksRunInOrderAfterTheLockAndBeforeAnyChange() {
        doThrow(new IllegalStateException("blocked")).when(negotiationService).assertReadyForSigning(tenant, contract);
        assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        InOrder order = inOrder(contractRepo, signingPolicy, riskService, negotiationService);
        order.verify(contractRepo).findForUpdate(tenant.getValue(), id);
        order.verify(signingPolicy).assertCanSign(contract);
        order.verify(riskService).assertNoBlockingFindings(tenant, contract);
        order.verify(negotiationService).assertReadyForSigning(tenant, contract);
    }

    @Test
    void unresolvedVariablesStopTheSendBeforeNegotiationIsAsked() {
        when(variableResolver.findUnresolved(anyString())).thenReturn(List.of("{{employee_name}}"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        assertTrue(e.getMessage().contains("{{employee_name}}"));
        verifyNoInteractions(negotiationService);
        assertNothingChanged();
    }

    @Test
    void aContractWithNoPartiesStopsTheSend() {
        when(partyRepo.findByContract(id)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        verifyNoInteractions(negotiationService);
        assertNothingChanged();
    }

    @Test
    void aBlockingRiskFindingStopsTheSendBeforeNegotiationIsAsked() {
        doThrow(new IllegalStateException("A rule that blocks sending found a problem")).when(riskService).assertNoBlockingFindings(tenant, contract);
        assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        verifyNoInteractions(negotiationService);
        assertNothingChanged();
    }

    @Test
    void aSigningPolicyRefusalStopsEverything() {
        doThrow(new IllegalStateException("This contract type needs a qualified signature")).when(signingPolicy).assertCanSign(contract);
        assertThrows(IllegalStateException.class, () -> service.sendForSigning(tenant, id));
        verifyNoInteractions(riskService, negotiationService);
        assertNothingChanged();
    }
}

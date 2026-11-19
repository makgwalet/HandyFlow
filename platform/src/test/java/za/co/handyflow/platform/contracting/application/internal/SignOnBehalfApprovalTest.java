package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.OtpRateLimitRepository;
import za.co.handyflow.platform.contracting.dto.SignContractRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Staff recording a signature for a party can be put behind a second person's approval. The gate runs before the one-time code is
 * checked, so a blocked attempt uses up nothing, and a signature the party gives for themselves never asks for an approval.
 */
@ExtendWith(MockitoExtension.class)
class SignOnBehalfApprovalTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock SigningPolicy signingPolicy;
    @Mock OtpService otpService;
    @Mock ContractApprovalService approvals;
    @Mock ContractAuthorityService authority;
    @Mock OtpRateLimitRepository otpRateLimitRepo;
    @InjectMocks ContractingService service;

    TenantId tenant = TenantId.generate();
    UUID contractId = UUID.randomUUID(), partyId = UUID.randomUUID(), staff = UUID.randomUUID();
    Contract contract = mock(Contract.class);
    ContractParty party = mock(ContractParty.class);

    @BeforeEach
    void setUp() {
        lenient().when(contractRepo.findActiveById(tenant, contractId)).thenReturn(Optional.of(contract));
        lenient().when(partyRepo.findByContract(contractId)).thenReturn(List.of(party));
        lenient().when(party.getId()).thenReturn(partyId);
    }

    private SignContractRequest request() {
        return new SignContractRequest("123456", null);
    }

    @Test
    void aWitnessedSignatureAsksTheApprovalGateBeforeTheCodeIsChecked() {
        doThrow(new IllegalStateException("This needs a second person's approval first. Request approval, then try again."))
                .when(approvals).assertApproved(tenant, ApprovalAction.SIGN_ON_BEHALF, partyId);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.signContract(tenant, contractId, partyId, request(), "10.0.0.1", "agent", staff));
        assertTrue(e.getMessage().contains("second person"));
        verifyNoInteractions(otpService);
        verify(approvals, never()).consume(any(), any(), any(), any());
    }

    @Test
    void aSignatureTheCounterpartyGivesForThemselvesNeverAsksForApproval() {
        when(otpService.verify(eq(partyId.toString()), anyString())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.signContract(tenant, contractId, partyId, request(), "10.0.0.1", "agent", null));
        verify(approvals, never()).assertApproved(any(), any(), any());
        verify(approvals, never()).consume(any(), any(), any(), any());
    }

    @Test
    void aWrongCodeAfterApprovalDoesNotUseUpTheApproval() {
        when(otpService.verify(eq(partyId.toString()), anyString())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.signContract(tenant, contractId, partyId, request(), "10.0.0.1", "agent", staff));
        verify(approvals).assertApproved(tenant, ApprovalAction.SIGN_ON_BEHALF, partyId);
        verify(approvals, never()).consume(any(), any(), any(), any());
    }

    @Test
    void signingForSomeoneElseNeedsSigningAuthorityBeforeAnythingElseHappens() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Not authorised to sign this contract. Your authority does not include sign."))
                .when(authority).assertCurrentUser(eq(tenant), eq(za.co.handyflow.platform.contracting.domain.model.AuthorityAction.SIGN), any());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.signContract(tenant, contractId, partyId, request(), "10.0.0.1", "agent", staff));
        verifyNoInteractions(otpService);
        verify(approvals, never()).assertApproved(any(), any(), any());
    }

    @Test
    void aSignatureTheCounterpartyGivesForThemselvesNeverAsksForAuthority() {
        when(otpService.verify(eq(partyId.toString()), anyString())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.signContract(tenant, contractId, partyId, request(), "10.0.0.1", "agent", null));
        verifyNoInteractions(authority);
    }
}

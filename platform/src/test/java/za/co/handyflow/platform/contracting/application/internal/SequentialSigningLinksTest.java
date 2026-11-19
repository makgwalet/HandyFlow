package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.AddPartyRequest;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.SmsService;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Signing links go out in signing order. Parties sharing an order number are told together; the next group gets a working
 * link (never a placeholder) once the group before it has signed; nobody is told twice. A new party defaults to the next free
 * order number, and a malformed email address is refused when the party is added.
 */
@ExtendWith(MockitoExtension.class)
class SequentialSigningLinksTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock ContractSigningTokenRepository signingTokenRepo;
    @Mock SigningPolicy signingPolicy;
    @Mock ContractVariableResolver variableResolver;
    @Mock ContractRiskService riskService;
    @Mock ContractNegotiationService negotiationService;
    @Mock EmailService emailService;
    @Mock SmsService smsService;
    @Mock PublicSigningRateLimiter publicRateLimiter;
    @Mock ContractApprovalService approvals;
    @Mock SigningTokenService signingTokenService;
    @InjectMocks ContractingService service;

    TenantId tenant = TenantId.generate();
    Contract contract;
    ContractParty first, second, third;

    @BeforeEach
    void setUp() {
        TenantContext.setUserId(UUID.randomUUID().toString());
        ReflectionTestUtils.setField(service, "baseUrl", "https://app.test");
        contract = Contract.create(tenant, "CON-7", "NDA", "NDA", "<p>Body</p>", null, UUID.randomUUID());
        first = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "CLIENT", "Ann", "ann@example.com", null, null, 1);
        second = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "SUPPLIER", "Ben", "ben@example.com", null, null, 2);
        third = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "WITNESS", "Cy", "cy@example.com", null, null, 2);
        lenient().when(contractRepo.findForUpdate(tenant.getValue(), contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(variableResolver.findUnresolved(anyString())).thenReturn(List.of());
        lenient().when(signingTokenService.sha256(anyString())).thenReturn("hash");
        lenient().when(signingTokenService.generateToken(any(), any(), any()))
                .thenAnswer(i -> "tok-" + i.getArgument(1));
        lenient().when(signingTokenRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private void parties(ContractParty... ps) {
        lenient().when(partyRepo.findByContract(contract.getId())).thenReturn(List.of(ps));
    }

    @Test
    void sendingTellsOnlyTheFirstGroup() {
        parties(first, second, third);
        service.sendForSigning(tenant, contract.getId());

        verify(emailService).send(eq("ann@example.com"), contains("Please sign"), contains("/sign/tok-" + first.getId()));
        verify(emailService, never()).send(eq("ben@example.com"), anyString(), anyString());
        verify(emailService, never()).send(eq("cy@example.com"), anyString(), anyString());
        verify(signingTokenRepo, times(1)).save(any());
    }

    @Test
    void partiesSharingTheLowestNumberAreToldTogether() {
        ContractParty twin = ContractParty.create(tenant, contract.getId(), "INDIVIDUAL", "CLIENT", "Di", "di@example.com", null, null, 1);
        parties(first, twin, second);
        service.sendForSigning(tenant, contract.getId());

        verify(emailService).send(eq("ann@example.com"), anyString(), anyString());
        verify(emailService).send(eq("di@example.com"), anyString(), anyString());
        verify(emailService, never()).send(eq("ben@example.com"), anyString(), anyString());
    }

    @Test
    void whenTheFirstGroupHasSignedTheNextGroupGetsARealLink() {
        contract.send();
        first.setSigningStatus("SIGNED");
        parties(first, second, third);
        when(signingTokenRepo.findActiveByPartyId(any())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "notifyNextParty", contract, List.of(first, second, third));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("ben@example.com"), contains("your turn"), body.capture());
        verify(emailService).send(eq("cy@example.com"), contains("your turn"), anyString());
        verify(emailService, never()).send(eq("ann@example.com"), anyString(), anyString());
        assertTrue(body.getValue().contains("https://app.test/sign/tok-" + second.getId()));
        assertFalse(body.getValue().contains("/sign/null"));
    }

    @Test
    void someoneAlreadyHoldingAWorkingLinkIsNotToldAgain() {
        contract.send();
        first.setSigningStatus("SIGNED");
        when(signingTokenRepo.findActiveByPartyId(second.getId())).thenReturn(Optional.of(mock(ContractSigningToken.class)));
        when(signingTokenRepo.findActiveByPartyId(third.getId())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "notifyNextParty", contract, List.of(first, second, third));

        verify(emailService, never()).send(eq("ben@example.com"), anyString(), anyString());
        verify(emailService).send(eq("cy@example.com"), anyString(), anyString());
    }

    @Test
    void aLaterSignersLinkCannotBeResentBeforeTheirTurn() {
        contract.send();
        parties(first, second);
        when(partyRepo.findByTenantAndIdForUpdate(tenant, second.getId())).thenReturn(Optional.of(second));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.resendSigningLink(tenant, contract.getId(), second.getId()));
        assertTrue(e.getMessage().contains("turn has not come yet"));
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void aNewPartyDefaultsToTheNextFreeOrder() {
        parties(first, second, third);
        ArgumentCaptor<ContractParty> saved = ArgumentCaptor.forClass(ContractParty.class);
        service.addParty(tenant, contract.getId(), new AddPartyRequest("INDIVIDUAL", "CLIENT", "Eve", "eve@example.com", null, null, null, null));
        verify(partyRepo).save(saved.capture());
        assertEquals(3, saved.getValue().getSigningOrder());
    }

    @Test
    void anExplicitOrderIsKeptSoPartiesCanSignTogether() {
        parties(first);
        ArgumentCaptor<ContractParty> saved = ArgumentCaptor.forClass(ContractParty.class);
        service.addParty(tenant, contract.getId(), new AddPartyRequest("INDIVIDUAL", "CLIENT", "Eve", "eve@example.com", null, null, null, 1));
        verify(partyRepo).save(saved.capture());
        assertEquals(1, saved.getValue().getSigningOrder());
    }

    @Test
    void aMalformedEmailIsRefused() {
        parties();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.addParty(tenant, contract.getId(), new AddPartyRequest("INDIVIDUAL", "CLIENT", "Eve", "rtrtrt", null, null, null, null)));
        assertTrue(e.getMessage().contains("rtrtrt"));
        verify(partyRepo, never()).save(any());
    }
}

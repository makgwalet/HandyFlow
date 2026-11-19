package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractApprovalServiceTest {

    @Mock ContractApprovalPolicyRepository policyRepo;
    @Mock ContractApprovalRepository approvalRepo;
    @Mock ContractApprovalEventRepository eventRepo;
    @Mock ContractRepository contractRepo;
    @Mock ContractPartyRepository partyRepo;
    @Mock ContractClauseRepository clauseRepo;
    @Mock ContractTemplateRepository templateRepo;
    ContractApprovalService service;

    TenantId tenant = TenantId.generate();
    UUID maker = UUID.randomUUID(), checker = UUID.randomUUID();
    Contract contract;
    final List<ContractApproval> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ContractApprovalService(policyRepo, approvalRepo, eventRepo, contractRepo, partyRepo, clauseRepo, templateRepo);
        contract = Contract.create(tenant, "CTR-1", "Services", "SERVICE", "<p>Original</p>", null, maker);
        lenient().when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(partyRepo.findByContract(contract.getId())).thenReturn(List.of());
        lenient().when(policyRepo.findOne(any(), any())).thenReturn(Optional.empty());
        lenient().when(approvalRepo.save(any())).thenAnswer(i -> { ContractApproval a = i.getArgument(0); if (!store.contains(a)) store.add(a); return a; });
        lenient().when(approvalRepo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(approvalRepo.findOpenForUpdate(eq(tenant.getValue()), any(), any())).thenAnswer(i -> store.stream()
                .filter(a -> a.getAction() == i.getArgument(1) && a.getSubjectId().equals(i.getArgument(2)) && a.isOpen()).findFirst());
        lenient().when(approvalRepo.findForUpdate(eq(tenant.getValue()), any())).thenAnswer(i -> store.stream().filter(a -> a.getId().equals(i.getArgument(1))).findFirst());
    }

    private ContractApproval requested() { return service.request(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId(), maker, "please"); }

    private static final Set<String> MANAGER = Set.of("CONTRACTS_MANAGE");

    // ── policy ──

    @Test
    void withNoStoredPolicyTheSafeDefaultsApply() {
        var all = service.policies(tenant);
        assertEquals(4, all.size());
        assertTrue(all.stream().allMatch(ContractApprovalService.EffectivePolicy::isDefault));
        assertTrue(service.policy(tenant, ApprovalAction.SEND_FOR_SIGNING).required());
        assertFalse(service.policy(tenant, ApprovalAction.TERMINATE).required());
    }

    @Test
    void aTenantCanSwitchAnActionOffOrOn() {
        ContractApprovalPolicy p = ContractApprovalPolicy.create(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING);
        p.set(false, false, maker);
        when(policyRepo.findOne(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING)).thenReturn(Optional.of(p));
        assertFalse(service.policy(tenant, ApprovalAction.SEND_FOR_SIGNING).required());
        assertDoesNotThrow(() -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        verifyNoInteractions(approvalRepo);
    }

    @Test
    void settingAPolicyRecordsWhoChangedIt() {
        when(policyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var p = service.setPolicy(tenant, ApprovalAction.TERMINATE, true, false, maker);
        assertTrue(p.required());
        assertFalse(p.allowSoleUserException());
        assertFalse(p.isDefault());
        ArgumentCaptor<ContractApprovalPolicy> c = ArgumentCaptor.forClass(ContractApprovalPolicy.class);
        verify(policyRepo).save(c.capture());
        assertEquals(tenant.getValue(), c.getValue().getTenantId());
        assertEquals(maker, c.getValue().getUpdatedBy());
    }

    // ── the gate ──

    @Test
    void withoutAnApprovalTheActionIsBlocked() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        assertTrue(e.getMessage().contains("second person"));
    }

    @Test
    void aPendingRequestStillBlocksIt() {
        requested();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        assertTrue(e.getMessage().contains("Waiting for approval"));
    }

    @Test
    void anApprovalByAnotherPersonLetsItThroughAndIsUsedUpOnce() {
        ContractApproval a = requested();
        service.approve(tenant, a.getId(), checker, MANAGER, "fine", false, null);
        assertDoesNotThrow(() -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        service.consume(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId(), maker);
        assertEquals("CONSUMED", a.getStatus());
        assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
    }

    @Test
    void changingTheContractAfterApprovalInvalidatesIt() {
        ContractApproval a = requested();
        service.approve(tenant, a.getId(), checker, MANAGER, null, false, null);
        contract.updateBody("<p>Changed after approval</p>");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        assertTrue(e.getMessage().contains("changed since it was approved"));
        // and the next look at the list records that it no longer stands
        when(approvalRepo.findByStatus(tenant.getValue(), null)).thenReturn(List.of(a));
        service.list(tenant, null);
        assertEquals("INVALIDATED", a.getStatus());
        verify(eventRepo, atLeastOnce()).save(argThat(ev -> "INVALIDATED".equals(ev.getEventType())));
    }

    @Test
    void anApprovalCannotBeGivenForWordingThatChangedAfterTheRequest() {
        ContractApproval a = requested();
        contract.updateBody("<p>Sneaky edit</p>");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.approve(tenant, a.getId(), checker, MANAGER, null, false, null));
        assertTrue(e.getMessage().contains("changed"));
        assertFalse(a.isApproved());
    }

    @Test
    void requestingAgainAfterAChangeReplacesTheOldRequest() {
        ContractApproval first = requested();
        assertSame(first, requested(), "unchanged subject: same request is reused");
        contract.updateBody("<p>Edited</p>");
        ContractApproval second = requested();
        assertNotSame(first, second);
        assertEquals("INVALIDATED", first.getStatus());
        assertEquals("PENDING", second.getStatus());
    }

    @Test
    void requestingWhenNotRequiredIsRefused() {
        ContractApprovalPolicy p = ContractApprovalPolicy.create(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING);
        p.set(false, true, maker);
        when(policyRepo.findOne(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING)).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, this::requested);
    }

    // ── separation of duties ──

    @Test
    void theRequesterCannotApproveButASoleUserExceptionIsAuditedWithItsReason() {
        ContractApproval a = requested();
        assertThrows(IllegalStateException.class, () -> service.approve(tenant, a.getId(), maker, MANAGER, null, false, null));
        service.approve(tenant, a.getId(), maker, MANAGER, null, true, "I am the only user in this company");
        assertTrue(a.isApproved());
        assertTrue(a.isSoleUserException());
        ArgumentCaptor<ContractApprovalEvent> ev = ArgumentCaptor.forClass(ContractApprovalEvent.class);
        verify(eventRepo, atLeastOnce()).save(ev.capture());
        ContractApprovalEvent last = ev.getAllValues().get(ev.getAllValues().size() - 1);
        assertEquals("APPROVED_SOLE_USER_EXCEPTION", last.getEventType());
        assertTrue(last.getDetail().contains("only user"));
        assertEquals(maker, last.getActorUserId());
    }

    @Test
    void theExceptionIsRefusedWhenThePolicyForbidsIt() {
        ContractApprovalPolicy p = ContractApprovalPolicy.create(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING);
        p.set(true, false, maker);
        when(policyRepo.findOne(tenant.getValue(), ApprovalAction.SEND_FOR_SIGNING)).thenReturn(Optional.of(p));
        ContractApproval a = requested();
        assertThrows(IllegalStateException.class, () -> service.approve(tenant, a.getId(), maker, MANAGER, null, true, "I am the only user in this company"));
        assertTrue(a.isPending());
    }

    @Test
    void aRoleThatCannotDecideThisKindOfApprovalIsRefused() {
        ContractApproval a = requested();
        assertThrows(AccessDeniedException.class, () -> service.approve(tenant, a.getId(), checker, Set.of("CONTRACTS_TEMPLATES"), null, false, null));
        assertThrows(AccessDeniedException.class, () -> service.reject(tenant, a.getId(), checker, Set.of("CONTRACTS_READ"), "no"));
        assertTrue(a.isPending());
    }

    @Test
    void rejectedWithoutAnotherChanceAndAuditedThroughout() {
        ContractApproval a = requested();
        service.reject(tenant, a.getId(), checker, MANAGER, "Missing the schedule");
        assertEquals("REJECTED", a.getStatus());
        assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        verify(eventRepo, times(2)).save(any());   // REQUESTED, REJECTED
    }

    // ── tenant isolation ──

    @Test
    void anotherTenantCannotSeeOrDecideOrRequestForThisSubject() {
        TenantId other = TenantId.generate();
        ContractApproval a = requested();
        when(approvalRepo.findForUpdate(eq(other.getValue()), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.approve(other, a.getId(), checker, MANAGER, null, false, null));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(other, a.getId(), maker));
        when(contractRepo.findActiveById(other, contract.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.request(other, ApprovalAction.SEND_FOR_SIGNING, contract.getId(), maker, null));
        when(approvalRepo.findOne(eq(other.getValue()), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.events(other, a.getId()));
    }

    @Test
    void hashesChangeWhenAnyPartyDetailChanges() {
        ContractParty p = mock(ContractParty.class);
        when(p.getFullName()).thenReturn("A Person"); when(p.getEmail()).thenReturn("a@example.com");
        when(partyRepo.findByContract(contract.getId())).thenReturn(List.of(p));
        ContractApproval a = requested();
        service.approve(tenant, a.getId(), checker, MANAGER, null, false, null);
        assertDoesNotThrow(() -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
        when(p.getEmail()).thenReturn("someone-else@example.com");
        assertThrows(IllegalStateException.class, () -> service.assertApproved(tenant, ApprovalAction.SEND_FOR_SIGNING, contract.getId()));
    }

    @Test
    void theHashDoesNotConfuseShiftedFieldBoundaries() {
        assertNotEquals(ContractApprovalService.sha256(List.of("ab", "c")), ContractApprovalService.sha256(List.of("a", "bc")));
    }
}

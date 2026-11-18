package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractChangeProposal;
import za.co.handyflow.platform.contracting.domain.model.ContractVersion;
import za.co.handyflow.platform.contracting.domain.repository.ContractChangeProposalRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractVersionRepository;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.ProposalView;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractRedlineServiceTest {

    static final String BASE = "<p>One.</p><p>Two.</p><p>Three.</p>";

    @Mock ContractRepository contractRepo;
    @Mock ContractVersionRepository versionRepo;
    @Mock ContractChangeProposalRepository proposalRepo;
    @InjectMocks ContractRedlineService service;

    TenantId tenant = TenantId.generate();
    Contract contract;
    int latest = 1;

    @BeforeEach
    void setUp() {
        contract = Contract.create(tenant, "C-1", "Deal", "SERVICE_AGREEMENT", BASE, null, UUID.randomUUID());
        lenient().when(contractRepo.findForUpdate(tenant.getValue(), contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(versionRepo.latestVersionNo(tenant.getValue(), contract.getId())).thenAnswer(i -> latest);
        lenient().when(versionRepo.findByTenantIdAndContractIdAndVersionNo(eq(tenant.getValue()), eq(contract.getId()), anyInt()))
                .thenAnswer(i -> Optional.of(ContractVersion.of(tenant.getValue(), contract.getId(), i.getArgument(2), BASE, "CREATED", null, null)));
        lenient().when(proposalRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ContractChangeProposal open(String body) {
        ContractChangeProposal p = ContractChangeProposal.create(tenant.getValue(), contract.getId(), 1, body, "n", UUID.randomUUID());
        when(proposalRepo.findByTenantIdAndId(tenant.getValue(), p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void proposingTheSameTextIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.propose(tenant, contract.getId(), BASE, null, null));
    }

    @Test
    void proposalBodyIsSanitised() {
        ProposalView v = service.propose(tenant, contract.getId(), "<p>One.</p><p>Two.</p><p>Three.</p><script>alert(1)</script><p>Four.</p>", null, null);
        assertEquals(1, v.hunks());
        assertFalse(v.segments().toString().contains("script"));
    }

    @Test
    void signedOrSentContractsCannotBeChanged() {
        contract.markSigned();
        assertThrows(IllegalStateException.class, () -> service.propose(tenant, contract.getId(), "<p>x</p>", null, null));
    }

    @Test
    void applyingTakesOnlyTheAcceptedChangesAndRecordsAVersion() {
        ContractChangeProposal p = open("<p>One.</p><p>Three.</p><p>Four.</p>");   // hunk 0: drop Two; hunk 1: add Four
        ProposalView v = service.apply(tenant, p.getId(), List.of(1), "ok", UUID.randomUUID());
        assertEquals("<p>One.</p><p>Two.</p><p>Three.</p><p>Four.</p>", contract.getBody());
        assertEquals("APPLIED", v.status());
        assertEquals("1", p.getAcceptedHunks());
        verify(versionRepo).save(any(ContractVersion.class));
        verify(contractRepo).save(contract);
    }

    @Test
    void staleProposalCannotBeApplied() {
        ContractChangeProposal p = open("<p>One.</p><p>Three.</p>");
        latest = 2;   // the contract changed after the proposal was made
        assertThrows(IllegalStateException.class, () -> service.apply(tenant, p.getId(), List.of(0), null, null));
        assertEquals(BASE, contract.getBody());
    }

    @Test
    void applyNeedsAtLeastOneValidChange() {
        ContractChangeProposal p = open("<p>One.</p><p>Three.</p>");
        assertThrows(IllegalArgumentException.class, () -> service.apply(tenant, p.getId(), List.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.apply(tenant, p.getId(), List.of(5), null, null));
        assertEquals(BASE, contract.getBody());
    }

    @Test
    void decidedProposalCannotBeDecidedAgain() {
        ContractChangeProposal p = open("<p>One.</p><p>Three.</p>");
        service.reject(tenant, p.getId(), "not agreed", null);
        assertEquals("REJECTED", p.getStatus());
        assertThrows(IllegalStateException.class, () -> service.apply(tenant, p.getId(), List.of(0), null, null));
        assertThrows(IllegalStateException.class, () -> service.reject(tenant, p.getId(), "again", null));
    }

    @Test
    void rejectNeedsAReason() {
        ContractChangeProposal p = open("<p>One.</p><p>Three.</p>");
        assertThrows(IllegalArgumentException.class, () -> service.reject(tenant, p.getId(), " ", null));
    }
}

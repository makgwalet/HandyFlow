package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractClause;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseUsageRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.dto.ContractClauseDtos.ClauseResponse;
import za.co.handyflow.platform.contracting.dto.ContractClauseDtos.InsertClauseRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractClauseServiceTest {

    @Mock ContractClauseRepository clauseRepo;
    @Mock ContractClauseUsageRepository usageRepo;
    @Mock ContractRepository contractRepo;
    @Mock ContractRedlineService redline;
    @Mock za.co.handyflow.platform.documents.application.DocumentsFacade documents;
    @Mock ContractApprovalService approvals;
    @InjectMocks ContractClauseService service;

    TenantId tenant = TenantId.generate();
    LocalDate today = LocalDate.now();

    private ContractClause published(LocalDate next) {
        ContractClause c = ContractClause.create(tenant.getValue(), "Domicilium", "DOMICILIUM", "<p>Reviewer wording.</p>", null, null, null);
        c.approve("A Attorney", "Admitted attorney", today.minusDays(1), next, null, today);
        c.publish(today);
        lenient().when(clauseRepo.findByTenantIdAndId(tenant.getValue(), c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @BeforeEach
    void setUp() { lenient().when(clauseRepo.save(any())).thenAnswer(i -> i.getArgument(0)); }

    @Test
    void withClauseInsertsAtEndStartOrAfterABlock() {
        String body = "<p>A</p><p>B</p>";
        String clause = "<p>X</p>";
        assertEquals("<p>A</p><p>B</p><p>X</p>", ContractClauseService.withClause(body, clause, null));
        assertEquals("<p>X</p><p>A</p><p>B</p>", ContractClauseService.withClause(body, clause, -1));
        assertEquals("<p>A</p><p>X</p><p>B</p>", ContractClauseService.withClause(body, clause, 0));
        assertEquals("<p>A</p><p>B</p><p>X</p>", ContractClauseService.withClause(body, clause, 99));
        assertEquals("<p>X</p>", ContractClauseService.withClause(null, clause, null));
    }

    @Test
    void insertingProposesTheChangeWithTheClauseIdSoUsageCanBeRecordedWhenApplied() {
        ContractClause c = published(null);
        Contract contract = Contract.create(tenant, "C-1", "Deal", "SERVICE_AGREEMENT", "<p>A</p>", null, null);
        when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        service.insert(tenant, contract.getId(), new InsertClauseRequest(c.getId(), null, "needed for domicilium"), null);
        verify(redline).propose(eq(tenant), eq(contract.getId()), eq("<p>A</p><p>Reviewer wording.</p>"), anyString(), any(), eq(c.getId()));
    }

    @Test
    void onlyAPublishedClauseCanBeInserted() {
        ContractClause draft = ContractClause.create(tenant.getValue(), "T", "OTHER", "<p>x</p>", null, null, null);
        when(clauseRepo.findByTenantIdAndId(tenant.getValue(), draft.getId())).thenReturn(Optional.of(draft));
        assertThrows(IllegalStateException.class, () -> service.insert(tenant, UUID.randomUUID(), new InsertClauseRequest(draft.getId(), null, null), null));
        verifyNoInteractions(redline);
    }

    @Test
    void publishingSupersedesThePreviousPublishedVersionOfTheSameClause() {
        ContractClause v1 = published(null);
        ContractClause v2 = v1.newVersion(2, null);
        v2.approve("A Attorney", "Admitted attorney", today.minusDays(1), null, null, today);
        when(clauseRepo.findByTenantIdAndId(tenant.getValue(), v2.getId())).thenReturn(Optional.of(v2));
        when(clauseRepo.findFirstByTenantIdAndFamilyIdAndStatus(tenant.getValue(), v1.getFamilyId(), "PUBLISHED")).thenReturn(Optional.of(v1));

        ClauseResponse r = service.publish(tenant, v2.getId());

        assertEquals("SUPERSEDED", v1.getStatus());
        assertEquals("PUBLISHED", r.status());
        verify(clauseRepo).saveAndFlush(v1);   // flushed first so the one-published-per-family index is never violated
    }

    @Test
    void usageStateReflectsTheClauseAsItStandsToday() {
        ContractClause c = published(today.plusDays(5));
        assertEquals("CURRENT", ContractClauseService.usageState(c, today));
        assertEquals("REVIEW_EXPIRED", ContractClauseService.usageState(c, today.plusDays(6)));
        c.withdraw("no longer valid");
        assertEquals("WITHDRAWN", ContractClauseService.usageState(c, today));
        assertEquals("NOT_REVIEWED", ContractClauseService.legalStatus(ContractClause.create(tenant.getValue(), "T", "OTHER", "<p>x</p>", null, null, null), today));
    }
}

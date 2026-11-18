package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractRiskDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractRiskServiceTest {

    @Mock ContractRepository contractRepo;
    @Mock ContractRiskRuleRepository ruleRepo;
    @Mock ContractRiskAssessmentRepository assessmentRepo;
    @Mock ContractClauseRepository clauseRepo;
    @Mock ContractClauseUsageRepository usageRepo;
    @Mock ContractVersionRepository versionRepo;
    @InjectMocks ContractRiskService service;

    TenantId tenant = TenantId.generate();
    LocalDate today = LocalDate.now();
    Contract contract;

    @BeforeEach
    void setUp() {
        contract = Contract.create(tenant, "CTR-1", "Service agreement", "SERVICE", "<p>Terms</p>", null, UUID.randomUUID());
        lenient().when(contractRepo.findActiveById(tenant, contract.getId())).thenReturn(Optional.of(contract));
        lenient().when(usageRepo.findByTenantIdAndContractIdOrderByInsertedAtDesc(tenant.getValue(), contract.getId())).thenReturn(List.of());
        lenient().when(versionRepo.latestVersionNo(tenant.getValue(), contract.getId())).thenReturn(2);
        lenient().when(assessmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    ContractRiskRule published(String type, String text, Number num, String severity, boolean blocks, String contractTypes, LocalDate next) {
        ContractRiskRule r = ContractRiskRule.create(tenant.getValue(), "Rule " + type, null, type, text, num == null ? null : new BigDecimal(num.toString()),
                contractTypes, severity, blocks, "Failed " + type, "Fix it", null, null, null);
        r.approve("A Attorney", "Admitted attorney", today.minusDays(1), next, null, today);
        r.publish(today);
        return r;
    }

    void rules(ContractRiskRule... r) { lenient().when(ruleRepo.findByTenantIdAndStatus(tenant.getValue(), "PUBLISHED")).thenReturn(List.of(r)); }

    @Test
    void noPublishedRulesMeansNotAssessedNeverLowRisk() {
        rules();
        AssessmentResponse a = service.assess(tenant, contract.getId(), UUID.randomUUID());
        assertEquals("NOT_ASSESSED", a.riskLevel());
        assertEquals(0, a.rulesEvaluated());
        assertTrue(a.findings().isEmpty());
        assertEquals(2, a.contractVersionNo());
    }

    @Test
    void rulesThatAllPassGiveNone() {
        rules(published("REQUIRES_PHRASE", "terms", null, "HIGH", false, null, null));
        AssessmentResponse a = service.assess(tenant, contract.getId(), UUID.randomUUID());
        assertEquals("NONE", a.riskLevel());
        assertEquals(1, a.rulesEvaluated());
    }

    @Test
    void levelIsTheHighestSeverityOfTheFailedRulesAndFindingsAreSortedWorstFirst() {
        rules(published("REQUIRES_VALUE", null, null, "LOW", false, null, null),
              published("REQUIRES_END_DATE", null, null, "CRITICAL", false, null, null),
              published("REQUIRES_PHRASE", "terms", null, "HIGH", false, null, null));
        AssessmentResponse a = service.assess(tenant, contract.getId(), UUID.randomUUID());
        assertEquals("CRITICAL", a.riskLevel());
        assertEquals(2, a.findings().size());
        assertEquals("CRITICAL", a.findings().get(0).severity());
        assertEquals("A Attorney", a.findings().get(0).reviewedBy());
    }

    @Test
    void rulesForOtherContractTypesOrWithLapsedReviewsAreSkippedNotRun() {
        ContractRiskRule other = published("REQUIRES_VALUE", null, null, "HIGH", false, "EMPLOYMENT", null);
        // Simulate time passing: a published rule whose next review date is now in the past.
        ContractRiskRule expired = published("REQUIRES_END_DATE", null, null, "HIGH", false, null, today.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(expired, "nextReviewDate", today.minusDays(1));
        rules(other, expired);
        AssessmentResponse a = service.assess(tenant, contract.getId(), UUID.randomUUID());
        assertEquals("NOT_ASSESSED", a.riskLevel());
        assertEquals(2, a.rulesSkipped());
        assertEquals(0, a.rulesEvaluated());
    }

    @Test
    void requiredClauseIsOnlySatisfiedByACurrentLibraryClause() {
        ContractClause cl = ContractClause.create(tenant.getValue(), "FM", "FORCE_MAJEURE", "<p>x</p>", null, null, null);
        cl.approve("A", "x", today.minusDays(1), null, null, today);
        cl.publish(today);
        ContractClauseUsage u = ContractClauseUsage.of(tenant.getValue(), contract.getId(), cl, null, 1, null);
        lenient().when(usageRepo.findByTenantIdAndContractIdOrderByInsertedAtDesc(tenant.getValue(), contract.getId())).thenReturn(List.of(u));
        lenient().when(clauseRepo.findByTenantIdAndId(tenant.getValue(), cl.getId())).thenReturn(Optional.of(cl));
        rules(published("REQUIRES_CLAUSE_CATEGORY", "FORCE_MAJEURE", null, "HIGH", false, null, null));
        assertEquals("NONE", service.assess(tenant, contract.getId(), UUID.randomUUID()).riskLevel());

        cl.withdraw("Law changed");
        AssessmentResponse after = service.assess(tenant, contract.getId(), UUID.randomUUID());
        assertEquals("HIGH", after.riskLevel(), "a withdrawn clause no longer satisfies the requirement");
        assertEquals(1, after.integrityFindings().size());
        assertEquals("WITHDRAWN", after.integrityFindings().get(0).state());
    }

    @Test
    void blockingRuleStopsSendingButNonBlockingDoesNot() {
        rules(published("REQUIRES_VALUE", null, null, "LOW", false, null, null));
        assertDoesNotThrow(() -> service.assertNoBlockingFindings(tenant, contract));
        rules(published("REQUIRES_VALUE", null, null, "LOW", false, null, null), published("REQUIRES_END_DATE", null, null, "HIGH", true, null, null));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.assertNoBlockingFindings(tenant, contract));
        assertTrue(e.getMessage().contains("Failed REQUIRES_END_DATE"));
        assertFalse(e.getMessage().contains("Failed REQUIRES_VALUE"));
    }

    @Test
    void noRulesNeverBlocks() {
        rules();
        assertDoesNotThrow(() -> service.assertNoBlockingFindings(tenant, contract));
    }

    @Test
    void assessmentIsStored() {
        rules();
        service.assess(tenant, contract.getId(), UUID.randomUUID());
        verify(assessmentRepo).save(any(ContractRiskAssessment.class));
    }

    @Test
    void levelHelper() {
        assertEquals("NOT_ASSESSED", ContractRiskService.level(0, List.of()));
        assertEquals("NONE", ContractRiskService.level(3, List.of()));
    }
}

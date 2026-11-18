package za.co.handyflow.platform.contracting.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractRiskDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Risk checks on a contract. Only published rules whose legal review has not lapsed, and that apply to the contract's type, are
 * run. If none applies the result is NOT_ASSESSED: nothing was checked, which is not the same as low risk. The level is the highest
 * severity among the failed rules (no scoring or weighting is applied). A rule marked "blocks sending" stops the contract being sent for
 * signing while it fails. The system supplies no rules; a finding says a reviewer's rule failed, not that the contract is unlawful.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractRiskService {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ContractRepository contractRepo;
    private final ContractRiskRuleRepository ruleRepo;
    private final ContractRiskAssessmentRepository assessmentRepo;
    private final ContractClauseRepository clauseRepo;
    private final ContractClauseUsageRepository usageRepo;
    private final ContractVersionRepository versionRepo;

    /** Result of running the rules, before it is stored. */
    record Outcome(String level, int evaluated, int skipped, List<Finding> findings, List<IntegrityFinding> integrity) {}

    @Transactional
    public AssessmentResponse assess(TenantId tenantId, UUID contractId, UUID by) {
        Contract c = contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        Outcome o = run(tenantId, c);
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), contractId);
        ContractRiskAssessment a = assessmentRepo.save(ContractRiskAssessment.of(tenantId.getValue(), contractId, latest > 0 ? latest : null, o.level(),
                o.evaluated(), o.skipped(), write(new Stored(o.findings(), o.integrity())), by));
        log.info("Risk check on contract={} level={} evaluated={} findings={}", c.getContractNumber(), o.level(), o.evaluated(), o.findings().size());
        return toResponse(a, o.findings(), o.integrity());
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> history(TenantId tenantId, UUID contractId) {
        contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        return assessmentRepo.findTop20ByTenantIdAndContractIdOrderByAssessedAtDesc(tenantId.getValue(), contractId).stream().map(a -> {
            Stored s = read(a.getFindingsJson());
            return toResponse(a, s.findings(), s.integrity());
        }).toList();
    }

    /** Called before a contract is sent for signing: refuses while a published rule marked "blocks sending" fails. */
    @Transactional(readOnly = true)
    public void assertNoBlockingFindings(TenantId tenantId, Contract c) {
        List<Finding> blocking = run(tenantId, c).findings().stream().filter(Finding::blocksSending).toList();
        if (!blocking.isEmpty())
            throw new IllegalStateException("This contract cannot be sent for signing yet: " + blocking.stream()
                    .map(f -> f.ruleName() + " (" + f.message() + ")").collect(Collectors.joining("; ")));
    }

    // ── evaluation ───────────────────────────────────────────────────────────

    Outcome run(TenantId tenantId, Contract c) {
        LocalDate today = LocalDate.now();
        List<ClauseSnapshot> clauses = clausesIn(tenantId, c.getId());
        Set<String> currentCategories = clauses.stream().filter(s -> "CURRENT".equals(s.state())).map(ClauseSnapshot::category).collect(Collectors.toSet());
        RiskRuleEvaluator.Facts facts = new RiskRuleEvaluator.Facts(c.getContractType(), c.getBody(), c.getValueAmount(), c.getStartDate(), c.getEndDate(),
                c.isAutoRenew(), c.getRenewalNoticeDays(), currentCategories);

        List<Finding> findings = new ArrayList<>();
        int evaluated = 0, skipped = 0;
        for (ContractRiskRule r : ruleRepo.findByTenantIdAndStatus(tenantId.getValue(), "PUBLISHED")) {
            if (r.reviewExpired(today) || !r.appliesTo(c.getContractType())) { skipped++; continue; }
            evaluated++;
            RiskRuleEvaluator.evaluate(r, facts).ifPresent(msg -> findings.add(new Finding(r.getId(), r.getName(), r.getVersionNo(), r.getSeverity(), msg,
                    r.getRemediation(), r.isBlocksSending(), r.getReviewerName(), r.getReviewDate())));
        }
        findings.sort(Comparator.comparingInt((Finding f) -> ContractRiskRule.SEVERITIES.indexOf(f.severity())).reversed().thenComparing(Finding::ruleName));
        return new Outcome(level(evaluated, findings), evaluated, skipped, findings, integrity(clauses));
    }

    static String level(int evaluated, List<Finding> findings) {
        if (evaluated == 0) return "NOT_ASSESSED";
        if (findings.isEmpty()) return "NONE";
        return findings.stream().map(Finding::severity).max(Comparator.comparingInt(ContractRiskRule.SEVERITIES::indexOf)).orElse("NONE");
    }

    record ClauseSnapshot(UUID clauseId, String title, String category, int versionNo, String state) {}

    private List<ClauseSnapshot> clausesIn(TenantId tenantId, UUID contractId) {
        LocalDate today = LocalDate.now();
        List<ClauseSnapshot> out = new ArrayList<>();
        for (ContractClauseUsage u : usageRepo.findByTenantIdAndContractIdOrderByInsertedAtDesc(tenantId.getValue(), contractId))
            clauseRepo.findByTenantIdAndId(tenantId.getValue(), u.getClauseId()).ifPresent(cl ->
                    out.add(new ClauseSnapshot(cl.getId(), cl.getTitle(), cl.getCategory(), u.getClauseVersionNo(), ContractClauseService.usageState(cl, today))));
        return out;
    }

    static List<IntegrityFinding> integrity(List<ClauseSnapshot> clauses) {
        List<IntegrityFinding> out = new ArrayList<>();
        for (ClauseSnapshot s : clauses) {
            String msg = switch (s.state()) {
                case "WITHDRAWN" -> "This library clause has been withdrawn since it was put into the contract.";
                case "REVIEW_EXPIRED" -> "The legal review of this library clause has lapsed since it was put into the contract.";
                case "SUPERSEDED" -> "A newer version of this library clause has been published since it was put into the contract.";
                default -> null;
            };
            if (msg != null) out.add(new IntegrityFinding(s.clauseId(), s.title(), s.versionNo(), s.state(), msg));
        }
        return out;
    }

    // ── storage ──────────────────────────────────────────────────────────────

    record Stored(List<Finding> findings, List<IntegrityFinding> integrity) {}

    private static String write(Stored s) {
        try { return JSON.writeValueAsString(s); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not store the risk check", e); }
    }

    private static Stored read(String json) {
        try { return JSON.readValue(json, new TypeReference<Stored>() {}); } catch (JsonProcessingException e) { return new Stored(List.of(), List.of()); }
    }

    private static AssessmentResponse toResponse(ContractRiskAssessment a, List<Finding> findings, List<IntegrityFinding> integrity) {
        return new AssessmentResponse(a.getId(), a.getContractId(), a.getContractVersionNo(), a.getRiskLevel(), a.getRulesEvaluated(), a.getRulesSkipped(),
                findings, integrity, a.getAssessedAt());
    }
}

package za.co.handyflow.platform.crm.application.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.domain.model.ScoringRule;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.ScoringRuleRepository;
import za.co.handyflow.platform.crm.dto.LeadScoreResponse;
import za.co.handyflow.platform.crm.dto.ScoringRuleRequest;
import za.co.handyflow.platform.crm.dto.ScoringRuleResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Scoring rules, and the score of every lead. Scores are worked out when asked for, so they are never out of date. */
@Service
@RequiredArgsConstructor
public class LeadScoringService {
    static final int MAX_RULES = 50;

    private final ScoringRuleRepository ruleRepository;
    private final CustomerRepository customerRepository;
    private final CrmAuditService audit;

    @Transactional(readOnly = true)
    public List<ScoringRuleResponse> rules(TenantId tenantId) {
        return ruleRepository.findAllActive(tenantId).stream().map(LeadScoringService::toResponse).toList();
    }

    @Transactional
    public ScoringRuleResponse create(TenantId tenantId, ScoringRuleRequest req) {
        Condition c = new Condition(req.field(), req.op(), req.value());
        LeadScoring.checkRule(req.name(), c, req.points());
        if (ruleRepository.findAllActive(tenantId).size() >= MAX_RULES) throw new IllegalArgumentException("At most " + MAX_RULES + " scoring rules");
        var rule = ScoringRule.create(tenantId, req.name(), req.field(), req.op(), req.value(), req.points(), req.active() == null || req.active());
        var saved = ruleRepository.save(rule);
        audit.record(tenantId, za.co.handyflow.platform.crm.domain.model.AuditEntity.SCORING_RULE, saved.getId(), saved.getName(), za.co.handyflow.platform.crm.domain.model.AuditAction.CREATED, "%d points".formatted(saved.getPoints()));
        return toResponse(saved);
    }

    @Transactional
    public ScoringRuleResponse update(TenantId tenantId, UUID id, ScoringRuleRequest req) {
        var rule = require(tenantId, id);
        LeadScoring.checkRule(req.name(), new Condition(req.field(), req.op(), req.value()), req.points());
        rule.change(req.name(), req.field(), req.op(), req.value(), req.points(), req.active() == null || req.active());
        audit.record(tenantId, za.co.handyflow.platform.crm.domain.model.AuditEntity.SCORING_RULE, id, rule.getName(), za.co.handyflow.platform.crm.domain.model.AuditAction.UPDATED, rule.isActive() ? null : "Turned off");
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var rule = require(tenantId, id);
        rule.softDelete();
        ruleRepository.save(rule);
        audit.record(tenantId, za.co.handyflow.platform.crm.domain.model.AuditEntity.SCORING_RULE, id, rule.getName(), za.co.handyflow.platform.crm.domain.model.AuditAction.DELETED, null);
    }

    /** The score, band and reasons of every active lead. */
    @Transactional(readOnly = true)
    public List<LeadScoreResponse> scores(TenantId tenantId) {
        List<LeadScoring.Rule> rules = ruleRepository.findAllActive(tenantId).stream().filter(ScoringRule::isActive)
                .map(r -> new LeadScoring.Rule(r.getName(), new Condition(r.getField(), r.getOp(), r.getValue()), r.getPoints())).toList();
        return customerRepository.findAllActiveLeads(tenantId).stream().map(c -> {
            var res = LeadScoring.score(rules, LeadFacts.of(c));
            return new LeadScoreResponse(c.getId(), res.score(), res.band(), res.reasons());
        }).toList();
    }

    private ScoringRule require(TenantId tenantId, UUID id) {
        return ruleRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ScoringRule", id.toString()));
    }

    static ScoringRuleResponse toResponse(ScoringRule r) {
        return new ScoringRuleResponse(r.getId(), r.getName(), r.getField(), r.getOp(), r.getValue(), r.getPoints(), r.isActive(),
                LeadConditions.describe(new Condition(r.getField(), r.getOp(), r.getValue())));
    }
}

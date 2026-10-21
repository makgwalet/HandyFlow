package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.RoutingRule;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.RoutingRuleRepository;
import za.co.handyflow.platform.crm.dto.RouteRunResponse;
import za.co.handyflow.platform.crm.dto.RoutingRuleRequest;
import za.co.handyflow.platform.crm.dto.RoutingRuleResponse;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Routing rules, and giving unassigned leads an owner from them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadRoutingService {
    static final int MAX_RULES = 20;

    private final RoutingRuleRepository ruleRepository;
    private final CustomerRepository customerRepository;
    private final UserRecipientResolver userResolver;
    private final CrmAuditService audit;

    @Transactional(readOnly = true)
    public List<RoutingRuleResponse> rules(TenantId tenantId) {
        return ruleRepository.findAllActive(tenantId).stream().map(LeadRoutingService::toResponse).toList();
    }

    @Transactional
    public RoutingRuleResponse create(TenantId tenantId, RoutingRuleRequest req) {
        check(tenantId, req);
        var existing = ruleRepository.findAllActive(tenantId);
        if (existing.size() >= MAX_RULES) throw new IllegalArgumentException("At most " + MAX_RULES + " routing rules");
        int next = existing.stream().mapToInt(RoutingRule::getPosition).max().orElse(0) + 1;
        var rule = RoutingRule.create(tenantId, next, req.name(), req.field(), req.op(), req.value(), LeadRouting.joinIds(req.assigneeIds()), req.active() == null || req.active());
        var saved = ruleRepository.save(rule);
        audit.record(tenantId, AuditEntity.ROUTING_RULE, saved.getId(), saved.getName(), AuditAction.CREATED, null);
        return toResponse(saved);
    }

    @Transactional
    public RoutingRuleResponse update(TenantId tenantId, UUID id, RoutingRuleRequest req) {
        var rule = require(tenantId, id);
        check(tenantId, req);
        rule.change(req.name(), req.field(), req.op(), req.value(), LeadRouting.joinIds(req.assigneeIds()), req.active() == null || req.active());
        audit.record(tenantId, AuditEntity.ROUTING_RULE, id, rule.getName(), AuditAction.UPDATED, rule.isActive() ? null : "Turned off");
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var rule = require(tenantId, id);
        rule.softDelete();
        ruleRepository.save(rule);
        audit.record(tenantId, AuditEntity.ROUTING_RULE, id, rule.getName(), AuditAction.DELETED, null);
    }

    /** Sets the order rules are tried in. The ids must be exactly the account's rules. */
    @Transactional
    public List<RoutingRuleResponse> reorder(TenantId tenantId, List<UUID> ids) {
        var rules = ruleRepository.findAllActive(tenantId);
        if (ids == null || ids.size() != rules.size() || !new java.util.HashSet<>(ids).equals(new java.util.HashSet<>(rules.stream().map(RoutingRule::getId).toList()))) {
            throw new IllegalArgumentException("List every routing rule once to set their order");
        }
        for (RoutingRule r : rules) r.moveTo(ids.indexOf(r.getId()) + 1);
        ruleRepository.saveAll(rules);
        audit.record(tenantId, AuditEntity.ROUTING_RULE, null, "Routing order", AuditAction.REORDERED, null);
        return ruleRepository.findAllActive(tenantId).stream().map(LeadRoutingService::toResponse).toList();
    }

    /** Gives every open lead that has no owner to the first rule that fits. */
    @Transactional
    public RouteRunResponse routeUnassigned(TenantId tenantId) {
        var rules = ruleRepository.findAllActive(tenantId).stream().filter(RoutingRule::isActive).toList();
        int routed = 0, left = 0;
        for (Customer c : customerRepository.findAllActiveLeads(tenantId)) {
            if (c.getOwnerId() != null || c.getCustomerType() != CustomerType.LEAD || isClosed(c)) continue;
            if (assign(tenantId, c, rules)) routed++; else left++;
        }
        if (routed > 0) {
            log.info("[CRM] Routed {} unassigned leads tenant={}", routed, tenantId);
            audit.record(tenantId, AuditEntity.ROUTING_RULE, null, "Route unassigned leads", AuditAction.RUN, routed + (routed == 1 ? " lead was given an owner" : " leads were given an owner"));
        }
        return new RouteRunResponse(routed, left);
    }

    /** Used when a lead is created without a person behind it (import, web form). Never throws: a lead must not be lost because routing failed. */
    @Transactional
    public void routeNewLead(TenantId tenantId, Customer lead) {
        try {
            if (lead.getOwnerId() != null || lead.getCustomerType() != CustomerType.LEAD) return;
            assign(tenantId, lead, ruleRepository.findAllActive(tenantId).stream().filter(RoutingRule::isActive).toList());
        } catch (Exception e) {
            log.warn("[CRM] Could not route new lead={} tenant={}: {}", lead.getId(), tenantId, e.getMessage());
        }
    }

    private boolean assign(TenantId tenantId, Customer lead, List<RoutingRule> rules) {
        List<LeadRouting.Rule> view = new ArrayList<>();
        for (RoutingRule r : rules) {
            Condition cond = r.getField() == null ? null : new Condition(r.getField(), r.getOp(), r.getValue());
            view.add(new LeadRouting.Rule(r.getId(), r.getPosition(), cond, LeadRouting.parseIds(r.getAssigneeIds()), r.getNextIndex()));
        }
        var decision = LeadRouting.choose(view, LeadFacts.of(lead), u -> userResolver.resolveUser(tenantId, u).isPresent());
        if (decision.isEmpty()) return false;
        lead.assignOwner(decision.get().assignee(), null);
        customerRepository.save(lead);
        rules.stream().filter(r -> r.getId().equals(decision.get().ruleId())).findFirst().ifPresent(r -> { r.advanceTo(decision.get().newIndex()); ruleRepository.save(r); });
        return true;
    }

    private static boolean isClosed(Customer c) { return c.getPipelineStage() == LeadStage.WON || c.getPipelineStage() == LeadStage.LOST; }

    private void check(TenantId tenantId, RoutingRuleRequest req) {
        Condition c = req.field() == null && req.op() == null && (req.value() == null || req.value().isBlank()) ? null : new Condition(req.field(), req.op(), req.value());
        LeadRouting.checkRule(req.name(), c, req.assigneeIds());
        for (UUID u : req.assigneeIds()) {
            if (userResolver.resolveUser(tenantId, u).isEmpty()) throw new IllegalArgumentException("One of the people chosen is not a user of this account");
        }
    }

    private RoutingRule require(TenantId tenantId, UUID id) {
        return ruleRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id.toString()));
    }

    static RoutingRuleResponse toResponse(RoutingRule r) {
        Condition c = r.getField() == null ? null : new Condition(r.getField(), r.getOp(), r.getValue());
        return new RoutingRuleResponse(r.getId(), r.getName(), r.getPosition(), r.getField(), r.getOp(), r.getValue(), LeadConditions.describe(c), LeadRouting.parseIds(r.getAssigneeIds()), r.isActive());
    }
}

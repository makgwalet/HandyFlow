package za.co.handyflow.platform.approvals.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.ApprovalCompletedEvent;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.domain.model.ApprovalDelegation;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRequest;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;
import za.co.handyflow.platform.approvals.domain.model.ApprovalStep;
import za.co.handyflow.platform.approvals.domain.repository.ApprovalDelegationRepository;
import za.co.handyflow.platform.approvals.domain.repository.ApprovalRequestRepository;
import za.co.handyflow.platform.approvals.domain.repository.ApprovalRuleRepository;
import za.co.handyflow.platform.approvals.domain.repository.ApprovalStepRepository;
import za.co.handyflow.platform.approvals.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * ApprovalEngineService — backlog 1.1. See the design doc + addendum for
 * the full rationale; this class is the implementation of that design.
 * <p>
 * JSON SHAPES this class parses/produces (the actual contract behind
 * ApprovalRule.conditions/approverChain and ApprovalRequest.metadata):
 * <pre>
 * metadata:    {"totalAmount": 15000, "department": "FINANCE"}
 *              — flat, simple values only, captured at submission time.
 *
 * conditions:  {"totalAmount": {">=": 10000}}
 *              — one comparison per field, implicit AND across fields.
 *              Supported operators: ">=" "<=" ">" "<" "==" "!=" "in"
 *              (value is a JSON array for "in"). A field named in
 *              conditions but absent from metadata never matches — you
 *              can't satisfy a condition on data that wasn't provided.
 *
 * approverChain: [
 *                  {"type":"ROLE","value":"AP_MANAGE"},
 *                  {"type":"ROLE","value":"AP_MANAGE",
 *                   "excludeActorOfPreviousStep":true,
 *                   "condition":{"totalAmount":{">=":50000}}}
 *                ]
 *              — ordered array; each entry's own "condition" (optional)
 *              is evaluated against the SAME metadata as the rule match
 *              itself — an entry with no condition is always included,
 *              one whose condition evaluates false is simply never
 *              materialized as a step. This is backlog 1.1 Q2's
 *              branching-routing mechanism (see the addendum).
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngineService implements ApprovalFacade {

    private final ApprovalRequestRepository requestRepo;
    private final ApprovalStepRepository stepRepo;
    private final ApprovalRuleRepository ruleRepo;
    private final ApprovalDelegationRepository delegationRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ══════════════════════════════════════════════════════════════════════
    // Rule management — this module's own tenant-facing configuration
    // surface, NOT part of ApprovalFacade (see that interface's own note).
    // Called directly by ApprovalRuleController, same module.
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ApprovalRuleResponse> listRules(TenantId tenantId, String module, String entityType) {
        return ruleRepo.findByTenant(tenantId.getValue(), module, entityType)
                .stream().map(this::toRuleResponse).toList();
    }

    @Transactional
    public ApprovalRuleResponse createRule(TenantId tenantId, ApprovalRuleRequest req) {
        validateRuleJson(req.conditions(), req.approverChain());
        ApprovalRule rule = ApprovalRule.create(tenantId.getValue(), req.module(), req.entityType(),
                req.name(), req.priority(), req.conditions(),
                ApprovalRule.ApprovalMode.valueOf(req.approvalMode()), req.approverChain(), false);
        ruleRepo.save(rule);
        log.info("[Approvals] Rule created tenant={} module={} entityType={} name={}",
                tenantId, req.module(), req.entityType(), req.name());
        return toRuleResponse(rule);
    }

    @Transactional
    public ApprovalRuleResponse updateRule(TenantId tenantId, UUID id, ApprovalRuleRequest req) {
        ApprovalRule rule = ruleRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRule", id.toString()));
        if (rule.isPlatformDefault()) {
            throw new HandyFlowException("Platform default rules cannot be edited directly — " +
                    "create your own tenant rule with a lower priority number to override it instead.",
                    HttpStatus.BAD_REQUEST, "PLATFORM_DEFAULT_IMMUTABLE");
        }
        validateRuleJson(req.conditions(), req.approverChain());
        rule.update(req.name(), req.priority(), req.conditions(),
                ApprovalRule.ApprovalMode.valueOf(req.approvalMode()), req.approverChain(), req.active());
        ruleRepo.save(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public void deactivateRule(TenantId tenantId, UUID id) {
        ApprovalRule rule = ruleRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRule", id.toString()));
        if (rule.isPlatformDefault()) {
            throw new HandyFlowException("Platform default rules cannot be deactivated — " +
                    "create your own tenant rule with a lower priority number to override it instead.",
                    HttpStatus.BAD_REQUEST, "PLATFORM_DEFAULT_IMMUTABLE");
        }
        rule.deactivate();
        ruleRepo.save(rule);
    }

    private void validateRuleJson(String conditions, String approverChain) {
        try {
            if (conditions != null && !conditions.isBlank()) objectMapper.readTree(conditions);
            JsonNode chain = objectMapper.readTree(approverChain);
            if (!chain.isArray() || chain.isEmpty()) {
                throw new HandyFlowException("approverChain must be a non-empty JSON array",
                        HttpStatus.BAD_REQUEST, "INVALID_RULE_JSON");
            }
        } catch (HandyFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new HandyFlowException("conditions/approverChain must be valid JSON: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST, "INVALID_RULE_JSON");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ApprovalFacade — the cross-module contract
    // ══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ApprovalRequestResponse submit(TenantId tenantId, String module, String entityType,
                                          UUID entityId, UUID submittedBy, Map<String, Object> metadata) {
        return doSubmit(tenantId, module, entityType, entityId, submittedBy, metadata, null);
    }

    @Override
    @Transactional
    public ApprovalRequestResponse resubmit(TenantId tenantId, UUID originalRequestId,
                                            UUID submittedBy, Map<String, Object> metadata) {
        ApprovalRequest original = requestRepo.findById(originalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", originalRequestId.toString()));
        if (original.getStatus() != ApprovalRequest.Status.RETURNED_FOR_CORRECTION) {
            throw new HandyFlowException("Only a request RETURNED_FOR_CORRECTION can be resubmitted — " +
                    "current status is " + original.getStatus(), HttpStatus.BAD_REQUEST, "NOT_RESUBMITTABLE");
        }
        original.markResubmitted();
        requestRepo.save(original);
        return doSubmit(tenantId, original.getModule(), original.getEntityType(), original.getEntityId(),
                submittedBy, metadata, original.getId());
    }

    private ApprovalRequestResponse doSubmit(TenantId tenantId, String module, String entityType,
                                             UUID entityId, UUID submittedBy, Map<String, Object> metadata,
                                             UUID resubmittedFromId) {
        String metadataJson = writeJson(metadata);
        MatchedRule matched = matchRule(tenantId.getValue(), module, entityType, metadata);

        if (matched == null) {
            // No rule anywhere for this module+entityType — nothing gates
            // it. Auto-approved immediately, not left pending forever.
            ApprovalRequest req = ApprovalRequest.submit(tenantId, module, entityType, entityId,
                    null, null, submittedBy, metadataJson, resubmittedFromId);
            req.complete(ApprovalRequest.Status.APPROVED);
            requestRepo.save(req);
            publishCompletion(req, "APPROVED");
            log.info("[Approvals] No rule for module={} entityType={} tenant={} — auto-approved", module, entityType, tenantId);
            return toRequestResponse(req, List.of());
        }

        List<ChainEntry> survivors = new ArrayList<>();
        for (ChainEntry entry : matched.chain) {
            if (entry.condition == null || evaluateConditions(entry.condition, metadata)) {
                survivors.add(entry);
            }
        }

        ApprovalRequest req = ApprovalRequest.submit(tenantId, module, entityType, entityId,
                matched.rule.getId(), matched.rule.getApprovalMode(), submittedBy, metadataJson, resubmittedFromId);

        if (survivors.isEmpty()) {
            req.complete(ApprovalRequest.Status.APPROVED);
            requestRepo.save(req);
            publishCompletion(req, "APPROVED");
            log.info("[Approvals] Rule matched but every chain entry's own condition excluded it — auto-approved. request={}", req.getId());
            return toRequestResponse(req, List.of());
        }

        req.markInProgress();
        requestRepo.save(req);

        List<ApprovalStep> steps = new ArrayList<>();
        int order = 1;
        for (ChainEntry entry : survivors) {
            int stepOrder = matched.rule.getApprovalMode() == ApprovalRule.ApprovalMode.SEQUENTIAL ? order++ : 1;
            ApprovalStep step = ApprovalStep.create(req.getId(), stepOrder, entry.type,
                    entry.value, entry.name, entry.excludeActorOfPreviousStep);
            steps.add(step);
        }
        stepRepo.saveAll(steps);

        log.info("[Approvals] Submitted request={} module={} entityType={} entityId={} rule={} steps={}",
                req.getId(), module, entityType, entityId, matched.rule.getId(), steps.size());
        return toRequestResponse(req, steps.stream().map(this::toStepResponse).toList());
    }

    @Override
    @Transactional
    public ApprovalRequestResponse actOnStep(TenantId tenantId, UUID stepId, UUID actingUserId,
                                             List<String> actingUserAuthorities,
                                             String decision, String comment, String actorIp) {
        ApprovalStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalStep", stepId.toString()));
        if (step.getStatus() != ApprovalStep.Status.PENDING) {
            throw new HandyFlowException("This approval step has already been actioned",
                    HttpStatus.BAD_REQUEST, "STEP_NOT_PENDING");
        }

        ApprovalRequest request = requestRepo.findById(step.getApprovalRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", step.getApprovalRequestId().toString()));
        if (!request.getTenantId().getValue().equals(tenantId.getValue())) {
            throw new ResourceNotFoundException("ApprovalStep", stepId.toString());
        }
        if (request.isTerminal()) {
            throw new HandyFlowException("This approval request has already reached a final outcome",
                    HttpStatus.BAD_REQUEST, "REQUEST_ALREADY_TERMINAL");
        }

        requireAuthorizedActor(tenantId, step, actingUserId, actingUserAuthorities, request.getModule());

        List<ApprovalStep> allSteps = stepRepo.findByApprovalRequest(request.getId());

        if (step.isExcludeActorOfPreviousStep()) {
            allSteps.stream()
                    .filter(s -> s.getStepOrder() == step.getStepOrder() - 1)
                    .findFirst()
                    .ifPresent(prev -> {
                        if (prev.getActedBy() != null && prev.getActedBy().equals(actingUserId)) {
                            throw new HandyFlowException(
                                    "A different person must give this approval — you already actioned the prior step",
                                    HttpStatus.FORBIDDEN, "SAME_APPROVER");
                        }
                    });
        }

        if (request.getApprovalMode() == ApprovalRule.ApprovalMode.SEQUENTIAL) {
            boolean earlierStepStillOpen = allSteps.stream()
                    .anyMatch(s -> s.getStepOrder() < step.getStepOrder() && s.getStatus() == ApprovalStep.Status.PENDING);
            if (earlierStepStillOpen) {
                throw new HandyFlowException("An earlier approval step is still pending",
                        HttpStatus.BAD_REQUEST, "OUT_OF_ORDER");
            }
        }

        boolean approve = "APPROVE".equalsIgnoreCase(decision);
        if (approve) {
            step.approve(actingUserId, comment, actorIp);
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            step.reject(actingUserId, comment, actorIp);
        } else {
            throw new HandyFlowException("decision must be APPROVE or REJECT", HttpStatus.BAD_REQUEST, "INVALID_DECISION");
        }
        stepRepo.save(step);

        allSteps = stepRepo.findByApprovalRequest(request.getId()); // reload post-action
        resolveRequestOutcome(request, allSteps);
        requestRepo.save(request);

        return toRequestResponse(request, allSteps.stream().map(this::toStepResponse).toList());
    }

    private void resolveRequestOutcome(ApprovalRequest request, List<ApprovalStep> steps) {
        ApprovalRule.ApprovalMode mode = request.getApprovalMode();
        boolean anyPending = steps.stream().anyMatch(s -> s.getStatus() == ApprovalStep.Status.PENDING);
        boolean allApproved = steps.stream().allMatch(s -> s.getStatus() == ApprovalStep.Status.APPROVED);
        boolean anyApproved = steps.stream().anyMatch(s -> s.getStatus() == ApprovalStep.Status.APPROVED);
        boolean anyRejected = steps.stream().anyMatch(s -> s.getStatus() == ApprovalStep.Status.REJECTED);
        boolean allRejected = steps.stream().allMatch(s -> s.getStatus() == ApprovalStep.Status.REJECTED);

        switch (mode) {
            case SEQUENTIAL, PARALLEL_ALL -> {
                if (anyRejected) {
                    request.complete(ApprovalRequest.Status.REJECTED);
                    publishCompletion(request, "REJECTED");
                } else if (!anyPending && allApproved) {
                    request.complete(ApprovalRequest.Status.APPROVED);
                    publishCompletion(request, "APPROVED");
                }
                // else: still IN_PROGRESS, nothing to do
            }
            case PARALLEL_ANY_ONE -> {
                if (anyApproved) {
                    steps.stream().filter(s -> s.getStatus() == ApprovalStep.Status.PENDING)
                            .forEach(ApprovalStep::skip);
                    stepRepo.saveAll(steps);
                    request.complete(ApprovalRequest.Status.APPROVED);
                    publishCompletion(request, "APPROVED");
                } else if (!anyPending && allRejected) {
                    request.complete(ApprovalRequest.Status.REJECTED);
                    publishCompletion(request, "REJECTED");
                }
            }
        }
    }

    private void publishCompletion(ApprovalRequest request, String outcome) {
        eventPublisher.publishEvent(ApprovalCompletedEvent.of(
                request.getTenantId(), request.getModule(), request.getEntityType(),
                request.getEntityId(), request.getId(), outcome));
    }

    private void requireAuthorizedActor(TenantId tenantId, ApprovalStep step, UUID actingUserId,
                                        List<String> actingUserAuthorities, String module) {
        switch (step.getApproverType()) {
            case USER -> {
                UUID requiredUser = UUID.fromString(step.getApproverValue());
                if (requiredUser.equals(actingUserId)) return;
                boolean delegated = delegationRepo.findActiveByDelegator(tenantId.getValue(), requiredUser).stream()
                        .anyMatch(d -> d.coversToday(module) && d.getDelegateUserId().equals(actingUserId));
                if (!delegated) {
                    throw new HandyFlowException("You are not authorized to act on this approval step",
                            HttpStatus.FORBIDDEN, "NOT_AUTHORIZED");
                }
            }
            case ROLE -> {
                if (actingUserAuthorities == null || !actingUserAuthorities.contains(step.getApproverValue())) {
                    throw new HandyFlowException("You are not authorized to act on this approval step",
                            HttpStatus.FORBIDDEN, "NOT_AUTHORIZED");
                }
            }
            case MANAGER_OF_SUBMITTER, EXTERNAL_CONTACT -> throw new HandyFlowException(
                    "This step type is not actionable through this endpoint", HttpStatus.BAD_REQUEST, "UNSUPPORTED_STEP_TYPE");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovalRequestResponse> getLatestRequestForEntity(TenantId tenantId, String module,
                                                                       String entityType, UUID entityId) {
        return requestRepo.findLatestForEntity(tenantId, module, entityType, entityId)
                .map(r -> toRequestResponse(r, stepRepo.findByApprovalRequest(r.getId())
                        .stream().map(this::toStepResponse).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalStepResponse> getMyPendingSteps(TenantId tenantId, UUID userId, List<String> authorities) {
        return stepRepo.findPendingForApprover(userId.toString(), authorities == null ? List.of() : authorities)
                .stream().map(this::toStepResponse).toList();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Rule matching + condition evaluation
    // ══════════════════════════════════════════════════════════════════════

    private record ChainEntry(ApprovalStep.ApproverType type, String value, String name,
                              boolean excludeActorOfPreviousStep, JsonNode condition) {}

    private record MatchedRule(ApprovalRule rule, List<ChainEntry> chain) {}

    private MatchedRule matchRule(UUID tenantId, String module, String entityType, Map<String, Object> metadata) {
        List<ApprovalRule> candidates = new ArrayList<>(ruleRepo.findActiveTenantRules(tenantId, module, entityType));
        candidates.addAll(ruleRepo.findActiveGlobalRules(module, entityType));

        for (ApprovalRule rule : candidates) {
            if (evaluateConditions(rule.getConditions(), metadata)) {
                try {
                    return new MatchedRule(rule, parseChain(rule.getApproverChain()));
                } catch (Exception e) {
                    log.error("[Approvals] Rule={} has unparseable approverChain — skipping: {}", rule.getId(), e.getMessage());
                }
            }
        }
        return null;
    }

    private List<ChainEntry> parseChain(String approverChainJson) throws Exception {
        JsonNode arr = objectMapper.readTree(approverChainJson);
        List<ChainEntry> chain = new ArrayList<>();
        for (JsonNode node : arr) {
            ApprovalStep.ApproverType type = ApprovalStep.ApproverType.valueOf(node.get("type").asText());
            String value = node.has("value") ? node.get("value").asText() : null;
            String name = node.has("name") ? node.get("name").asText() : null;
            boolean exclude = node.has("excludeActorOfPreviousStep") && node.get("excludeActorOfPreviousStep").asBoolean();
            JsonNode condition = node.has("condition") ? node.get("condition") : null;
            chain.add(new ChainEntry(type, value, name, exclude, condition));
        }
        return chain;
    }

    private boolean evaluateConditions(String conditionsJson, Map<String, Object> metadata) {
        if (conditionsJson == null || conditionsJson.isBlank()) return true;
        try {
            return evaluateConditions(objectMapper.readTree(conditionsJson), metadata);
        } catch (Exception e) {
            log.error("[Approvals] Unparseable conditions JSON '{}': {}", conditionsJson, e.getMessage());
            return false;
        }
    }

    private boolean evaluateConditions(JsonNode conditions, Map<String, Object> metadata) {
        if (conditions == null || conditions.isMissingNode() || conditions.isNull()) return true;
        Iterator<String> fields = conditions.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!metadata.containsKey(field)) return false; // can't satisfy a condition on missing data
            Object actual = metadata.get(field);
            JsonNode comparison = conditions.get(field);
            Iterator<String> operators = comparison.fieldNames();
            while (operators.hasNext()) {
                String op = operators.next();
                JsonNode expected = comparison.get(op);
                if (!compare(actual, op, expected)) return false;
            }
        }
        return true;
    }

    private boolean compare(Object actual, String op, JsonNode expected) {
        if ("in".equals(op)) {
            if (!expected.isArray()) return false;
            for (JsonNode item : expected) {
                if (String.valueOf(actual).equals(item.asText())) return true;
            }
            return false;
        }
        if (actual instanceof Number actualNum) {
            double a = actualNum.doubleValue();
            double e = expected.asDouble();
            return switch (op) {
                case ">=" -> a >= e;
                case "<=" -> a <= e;
                case ">"  -> a > e;
                case "<"  -> a < e;
                case "==" -> a == e;
                case "!=" -> a != e;
                default -> false;
            };
        }
        String a = String.valueOf(actual);
        String e = expected.asText();
        return switch (op) {
            case "==" -> a.equals(e);
            case "!=" -> !a.equals(e);
            default -> false;
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mapping helpers
    // ══════════════════════════════════════════════════════════════════════

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new HandyFlowException("Failed to serialize approval metadata: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "METADATA_SERIALIZATION_FAILED");
        }
    }

    private ApprovalRequestResponse toRequestResponse(ApprovalRequest r, List<ApprovalStepResponse> steps) {
        return new ApprovalRequestResponse(r.getId(), r.getModule(), r.getEntityType(), r.getEntityId(),
                r.getStatus().name(), r.getSubmittedBy(), r.getSubmittedAt(), r.getCompletedAt(),
                r.getResubmittedFromId(), steps);
    }

    private ApprovalStepResponse toStepResponse(ApprovalStep s) {
        return new ApprovalStepResponse(s.getId(), s.getApprovalRequestId(), s.getStepOrder(),
                s.getApproverType().name(), s.getApproverValue(), s.getApproverName(),
                s.getStatus().name(), s.getActedBy(), s.getActedAt(), s.getComment());
    }

    private ApprovalRuleResponse toRuleResponse(ApprovalRule r) {
        return new ApprovalRuleResponse(r.getId(), r.getTenantId(), r.getModule(), r.getEntityType(),
                r.getName(), r.isActive(), r.getPriority(), r.getConditions(),
                r.getApprovalMode().name(), r.getApproverChain(), r.isPlatformDefault(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
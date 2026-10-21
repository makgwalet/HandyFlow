package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.Opportunity;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.OpportunityRepository;
import za.co.handyflow.platform.crm.dto.OpportunityRequest;
import za.co.handyflow.platform.crm.dto.OpportunityResponse;
import za.co.handyflow.platform.crm.dto.OpportunityStageRequest;
import za.co.handyflow.platform.crm.dto.OpportunitySummaryResponse;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Opportunities: many deals per company, each with its own stage. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final OpportunityRepository opportunityRepository;
    private final CustomerRepository customerRepository;
    private final UserRecipientResolver userResolver;
    private final WorkflowService workflowService;
    private final CrmAuditService audit;
    private final WebhookService webhookService;

    /** customerId narrows to one company; openOnly leaves out won and lost. */
    @Transactional(readOnly = true)
    public Page<OpportunityResponse> list(TenantId tenantId, UUID customerId, boolean openOnly, Pageable pageable) {
        Page<Opportunity> page = customerId != null
                ? opportunityRepository.findActiveByCustomer(tenantId, customerId, pageable)
                : openOnly ? opportunityRepository.findOpen(tenantId, pageable) : opportunityRepository.findAllActive(tenantId, pageable);
        Map<UUID, String> names = names(tenantId, page.getContent().stream().map(Opportunity::getCustomerId).distinct().toList());
        return page.map(o -> toResponse(o, names.get(o.getCustomerId())));
    }

    @Transactional(readOnly = true)
    public OpportunitySummaryResponse summary(TenantId tenantId) {
        var items = opportunityRepository.findEveryActive(tenantId).stream()
                .map(o -> new OpportunityRules.Item(o.getStage(), o.getAmount(), o.getExpectedCloseDate())).toList();
        return toSummary(OpportunityRules.summarise(items, LocalDate.now(ZONE)));
    }

    @Transactional
    public OpportunityResponse create(TenantId tenantId, OpportunityRequest req) {
        if (req.customerId() == null) throw new IllegalArgumentException("Choose the company");
        Customer customer = customerRepository.findActiveById(tenantId, req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId().toString()));
        UUID by = currentUserId();
        UUID owner = req.ownerId() != null ? checkOwner(tenantId, req.ownerId()) : by;
        Opportunity o = Opportunity.create(tenantId, customer.getId(), OpportunityRules.cleanName(req.name()), OpportunityRules.cleanAmount(req.amount()),
                req.expectedCloseDate(), owner, OpportunityRules.cleanNote(req.notes(), 4000, "The notes"), by);
        opportunityRepository.save(o);
        log.info("[CRM] Opportunity created id={} customer={} tenant={}", o.getId(), customer.getId(), tenantId);
        audit.record(tenantId, AuditEntity.OPPORTUNITY, o.getId(), o.getName(), AuditAction.CREATED, customer.getName());
        return toResponse(o, customer.getName());
    }

    @Transactional
    public OpportunityResponse update(TenantId tenantId, UUID id, OpportunityRequest req) {
        Opportunity o = require(tenantId, id);
        UUID owner = req.ownerId() != null && !req.ownerId().equals(o.getOwnerId()) ? checkOwner(tenantId, req.ownerId()) : req.ownerId();
        o.changeDetails(OpportunityRules.cleanName(req.name()), OpportunityRules.cleanAmount(req.amount()), req.expectedCloseDate(), owner,
                OpportunityRules.cleanNote(req.notes(), 4000, "The notes"));
        opportunityRepository.save(o);
        audit.record(tenantId, AuditEntity.OPPORTUNITY, o.getId(), o.getName(), AuditAction.UPDATED, null);
        return toResponse(o, names(tenantId, List.of(o.getCustomerId())).get(o.getCustomerId()));
    }

    @Transactional
    public OpportunityResponse changeStage(TenantId tenantId, UUID id, OpportunityStageRequest req) {
        Opportunity o = require(tenantId, id);
        String note = OpportunityRules.cleanNote(req.lostNote(), 500, "The note");
        OpportunityRules.checkMove(o.getStage(), req.stage(), req.lostReason(), note);
        o.moveTo(req.stage(), req.lostReason(), note);
        opportunityRepository.save(o);
        log.info("[CRM] Opportunity {} moved to {} tenant={}", id, req.stage(), tenantId);
        audit.record(tenantId, AuditEntity.OPPORTUNITY, id, o.getName(), AuditAction.STAGE_CHANGED, "Moved to " + req.stage().name().toLowerCase());
        if (req.stage() == za.co.handyflow.platform.crm.domain.model.OpportunityStage.WON) {
            customerRepository.findActiveById(tenantId, o.getCustomerId())
                    .ifPresent(c -> {
                        workflowService.fire(tenantId, za.co.handyflow.platform.crm.domain.model.WorkflowTrigger.OPPORTUNITY_WON, c, null);
                        webhookService.publish(tenantId, za.co.handyflow.platform.crm.domain.model.WebhookEvent.OPPORTUNITY_WON, c);
                    });
        }
        return toResponse(o, names(tenantId, List.of(o.getCustomerId())).get(o.getCustomerId()));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        Opportunity o = require(tenantId, id);
        o.softDelete();
        opportunityRepository.save(o);
        audit.record(tenantId, AuditEntity.OPPORTUNITY, id, o.getName(), AuditAction.DELETED, null);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Opportunity require(TenantId tenantId, UUID id) {
        return opportunityRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Opportunity", id.toString()));
    }

    private UUID checkOwner(TenantId tenantId, UUID ownerId) {
        if (userResolver.resolveUser(tenantId, ownerId).isEmpty()) throw new IllegalArgumentException("That person is not a user of this account");
        return ownerId;
    }

    private Map<UUID, String> names(TenantId tenantId, List<UUID> customerIds) {
        Map<UUID, String> out = new HashMap<>();
        if (customerIds.isEmpty()) return out;
        for (Customer c : customerRepository.findActiveByIds(tenantId, customerIds)) out.put(c.getId(), c.getName());
        return out;
    }

    static OpportunityResponse toResponse(Opportunity o, String customerName) {
        return new OpportunityResponse(o.getId(), o.getCustomerId(), customerName, o.getName(), o.getStage(), o.getStage().chance(),
                o.getAmount(), OpportunityRules.weighted(o.getStage(), o.getAmount()), o.getExpectedCloseDate(), o.getOwnerId(),
                o.getLostReason(), o.getLostNote(), o.getNotes(), o.getClosedAt(), o.getCreatedAt(), o.getUpdatedAt());
    }

    static OpportunitySummaryResponse toSummary(OpportunityRules.Summary s) {
        return new OpportunitySummaryResponse(s.openCount(), s.openValue(), s.weightedValue(), s.overdueCount(), s.wonCount(), s.wonValue(), s.lostCount(),
                s.byStage().stream().map(t -> new OpportunitySummaryResponse.StageTotal(t.stage(), t.count(), t.value())).toList());
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}

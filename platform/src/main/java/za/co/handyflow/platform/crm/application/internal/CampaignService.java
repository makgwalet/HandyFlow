package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.CampaignRules.Step;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.domain.model.Campaign;
import za.co.handyflow.platform.crm.domain.model.CampaignMember;
import za.co.handyflow.platform.crm.domain.model.CampaignStatus;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.JourneyRun;
import za.co.handyflow.platform.crm.domain.model.JourneyStep;
import za.co.handyflow.platform.crm.domain.model.MemberStatus;
import za.co.handyflow.platform.crm.domain.repository.CampaignMemberRepository;
import za.co.handyflow.platform.crm.domain.repository.CampaignRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.JourneyRunRepository;
import za.co.handyflow.platform.crm.domain.repository.JourneyStepRepository;
import za.co.handyflow.platform.crm.dto.AddMembersRequest;
import za.co.handyflow.platform.crm.dto.AddMembersResponse;
import za.co.handyflow.platform.crm.dto.CampaignMemberResponse;
import za.co.handyflow.platform.crm.dto.CampaignRequest;
import za.co.handyflow.platform.crm.dto.CampaignResponse;
import za.co.handyflow.platform.crm.dto.JourneyRunResponse;
import za.co.handyflow.platform.crm.dto.JourneyStepRequest;
import za.co.handyflow.platform.crm.dto.JourneyStepResponse;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Campaigns: a named outreach to a list of people, with a journey of timed steps that run for each person while the campaign is active.
 * A person leaves the journey when they respond, convert, or their lead is won or lost. A failing step is logged and never stops the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    /** The most steps one nightly sweep runs for one account, so a huge campaign cannot hold the scheduler. */
    static final int MAX_STEPS_PER_SWEEP = 1000;

    private final CampaignRepository campaigns;
    private final CampaignMemberRepository members;
    private final JourneyStepRepository stepRepository;
    private final JourneyRunRepository runRepository;
    private final CustomerRepository customerRepository;
    private final SegmentService segmentService;
    private final WorkflowService workflowService;
    private final UserRecipientResolver userResolver;
    private final CrmAuditService audit;

    @Transactional(readOnly = true)
    public List<CampaignResponse> list(TenantId tenantId) {
        Map<UUID, Map<MemberStatus, Integer>> counts = new HashMap<>();
        for (Object[] row : members.countByCampaignAndStatus(tenantId)) {
            counts.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(MemberStatus.class)).put((MemberStatus) row[1], ((Number) row[2]).intValue());
        }
        Map<UUID, Integer> stepCounts = new HashMap<>();
        stepRepository.findAllForTenant(tenantId).forEach(s -> stepCounts.merge(s.getCampaignId(), 1, Integer::sum));
        return campaigns.findAllActive(tenantId).stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), Map.of()), stepCounts.getOrDefault(c.getId(), 0))).toList();
    }

    @Transactional
    public CampaignResponse create(TenantId tenantId, CampaignRequest req) {
        CampaignRules.check(req.name(), req.goal(), req.startsOn(), req.endsOn());
        if (campaigns.findAllActive(tenantId).size() >= CampaignRules.MAX_CAMPAIGNS) throw new IllegalArgumentException("At most " + CampaignRules.MAX_CAMPAIGNS + " campaigns");
        var c = campaigns.save(Campaign.create(tenantId, req.name(), req.goal(), req.startsOn(), req.endsOn(), TenantContext.getCurrentUserId()));
        audit.record(tenantId, AuditEntity.CAMPAIGN, c.getId(), c.getName(), AuditAction.CREATED, c.getGoal());
        return toResponse(c, Map.of(), 0);
    }

    @Transactional
    public CampaignResponse update(TenantId tenantId, UUID id, CampaignRequest req) {
        var c = require(tenantId, id);
        if (c.getStatus() == CampaignStatus.COMPLETED) throw new IllegalArgumentException("A finished campaign cannot be changed");
        CampaignRules.check(req.name(), req.goal(), req.startsOn(), req.endsOn());
        c.change(req.name(), req.goal(), req.startsOn(), req.endsOn());
        campaigns.save(c);
        audit.record(tenantId, AuditEntity.CAMPAIGN, id, c.getName(), AuditAction.UPDATED, null);
        return one(tenantId, c);
    }

    @Transactional
    public CampaignResponse setStatus(TenantId tenantId, UUID id, CampaignStatus to) {
        var c = require(tenantId, id);
        CampaignStatus from = c.getStatus();
        CampaignRules.checkMove(from, to);
        if (from == to) return one(tenantId, c);
        if (to == CampaignStatus.ACTIVE && from == CampaignStatus.DRAFT && members.findForCampaign(tenantId, id).isEmpty()) {
            throw new IllegalArgumentException("Add people before starting the campaign");
        }
        c.moveTo(to);
        campaigns.save(c);
        AuditAction action = to == CampaignStatus.ACTIVE ? AuditAction.STARTED : AuditAction.STOPPED;
        audit.record(tenantId, AuditEntity.CAMPAIGN, id, c.getName(), action, from.name().toLowerCase() + " to " + to.name().toLowerCase());
        return one(tenantId, c);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var c = require(tenantId, id);
        if (c.getStatus() == CampaignStatus.ACTIVE) throw new IllegalArgumentException("Pause the campaign before deleting it");
        c.softDelete();
        campaigns.save(c);
        audit.record(tenantId, AuditEntity.CAMPAIGN, id, c.getName(), AuditAction.DELETED, null);
    }

    // ── Journey ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<JourneyStepResponse> steps(TenantId tenantId, UUID id) {
        require(tenantId, id);
        return stepRepository.findForCampaign(tenantId, id).stream().map(s -> stepResponse(s, null)).toList();
    }

    /** Replaces every step. People already part-way keep their count, so they carry on from where they were. */
    @Transactional
    public List<JourneyStepResponse> setSteps(TenantId tenantId, UUID id, List<JourneyStepRequest> req) {
        var c = require(tenantId, id);
        CampaignRules.checkEditable(c.getStatus());
        List<Step> rules = new ArrayList<>();
        if (req != null) for (var r : req) rules.add(new Step(r.dayOffset() == null ? -1 : r.dayOffset(), r.action(), r.text(), r.user()));
        CampaignRules.checkSteps(rules);
        for (int i = 0; i < rules.size(); i++) {
            Step s = rules.get(i);
            if (s.user() != null && (s.action() == za.co.handyflow.platform.crm.domain.model.WorkflowAction.ASSIGN_OWNER || s.action() == za.co.handyflow.platform.crm.domain.model.WorkflowAction.CREATE_TASK)
                    && userResolver.resolveUser(tenantId, s.user()).isEmpty()) {
                throw new IllegalArgumentException("Step " + (i + 1) + ": that person is not a user of this account");
            }
        }
        stepRepository.deleteForCampaign(tenantId, id);
        List<JourneyStep> saved = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Step s = rules.get(i);
            saved.add(stepRepository.save(JourneyStep.of(tenantId, id, i + 1, s.dayOffset(), s.action(), s.text(), s.user())));
        }
        audit.record(tenantId, AuditEntity.JOURNEY, id, c.getName(), AuditAction.UPDATED, saved.size() + (saved.size() == 1 ? " step" : " steps"));
        return saved.stream().map(s -> stepResponse(s, null)).toList();
    }

    // ── People ──────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CampaignMemberResponse> people(TenantId tenantId, UUID id) {
        require(tenantId, id);
        var rows = members.findForCampaign(tenantId, id);
        Map<UUID, String> names = names(tenantId, rows.stream().map(CampaignMember::getCustomerId).distinct().toList());
        return rows.stream().map(m -> new CampaignMemberResponse(m.getId(), m.getCustomerId(), names.getOrDefault(m.getCustomerId(), "Deleted customer"),
                m.getStatus(), m.getAddedAt(), m.getStatusAt(), m.getStepsDone(), m.isExited())).toList();
    }

    @Transactional
    public AddMembersResponse addPeople(TenantId tenantId, UUID id, AddMembersRequest req) {
        var c = require(tenantId, id);
        if (c.getStatus() == CampaignStatus.COMPLETED) throw new IllegalArgumentException("A finished campaign cannot be changed");
        List<UUID> wanted = new ArrayList<>();
        if (req.customerIds() != null) wanted.addAll(req.customerIds());
        if (req.segmentId() != null) wanted.addAll(segmentService.memberIds(tenantId, req.segmentId()));
        var existing = members.findForCampaign(tenantId, id);
        var already = new HashSet<UUID>();
        existing.forEach(m -> already.add(m.getCustomerId()));
        List<UUID> fresh = CampaignRules.toAdd(already, wanted);
        Map<UUID, String> found = names(tenantId, fresh);
        int added = 0;
        for (UUID customerId : fresh) {
            if (!found.containsKey(customerId)) continue;   // deleted or not in this account
            members.save(CampaignMember.of(tenantId, id, customerId));
            added++;
        }
        int alreadyIn = (int) wanted.stream().distinct().filter(already::contains).count();
        if (added > 0) audit.record(tenantId, AuditEntity.CAMPAIGN, id, c.getName(), AuditAction.UPDATED, "Added " + added + (added == 1 ? " person" : " people"));
        return new AddMembersResponse(added, alreadyIn, fresh.size() - added);
    }

    @Transactional
    public void removePerson(TenantId tenantId, UUID id, UUID memberId) {
        var c = require(tenantId, id);
        var m = members.findOne(tenantId, id, memberId).orElseThrow(() -> new ResourceNotFoundException("Campaign member", memberId.toString()));
        members.delete(m);
        audit.record(tenantId, AuditEntity.CAMPAIGN, id, c.getName(), AuditAction.UPDATED, "Removed one person");
    }

    @Transactional
    public CampaignMemberResponse setPersonStatus(TenantId tenantId, UUID id, UUID memberId, MemberStatus status) {
        require(tenantId, id);
        if (status == null) throw new IllegalArgumentException("Choose a status");
        var m = members.findOne(tenantId, id, memberId).orElseThrow(() -> new ResourceNotFoundException("Campaign member", memberId.toString()));
        m.setStatus(status);
        members.save(m);
        String name = names(tenantId, List.of(m.getCustomerId())).getOrDefault(m.getCustomerId(), "Deleted customer");
        return new CampaignMemberResponse(m.getId(), m.getCustomerId(), name, m.getStatus(), m.getAddedAt(), m.getStatusAt(), m.getStepsDone(), m.isExited());
    }

    @Transactional(readOnly = true)
    public List<JourneyRunResponse> recentRuns(TenantId tenantId, UUID id, int limit) {
        require(tenantId, id);
        var runs = runRepository.findRecent(tenantId, id, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
        Map<UUID, String> names = names(tenantId, runs.stream().map(JourneyRun::getCustomerId).distinct().toList());
        return runs.stream().map(r -> new JourneyRunResponse(r.getId(), r.getCustomerId(), names.getOrDefault(r.getCustomerId(), "Deleted customer"),
                r.getStepPosition(), r.getOutcome(), r.getDetail(), r.getRanAt())).toList();
    }

    // ── The daily sweep ─────────────────────────────────────────────────────
    /** Runs every step now due, for every active campaign in its dates. Returns how many steps ran. */
    @Transactional
    public int runDue(TenantId tenantId, LocalDate today) {
        int ran = 0;
        for (Campaign c : campaigns.findActiveByStatus(tenantId, CampaignStatus.ACTIVE)) {
            if (ran >= MAX_STEPS_PER_SWEEP) break;
            if (!CampaignRules.inWindow(c.getStartsOn(), c.getEndsOn(), today)) continue;
            try {
                ran += runCampaign(tenantId, c, today, MAX_STEPS_PER_SWEEP - ran);
            } catch (Exception e) {
                log.warn("[CRM] Journey for campaign={} tenant={} could not run: {}", c.getId(), tenantId, e.getMessage());
            }
        }
        return ran;
    }

    private int runCampaign(TenantId tenantId, Campaign c, LocalDate today, int budget) {
        var steps = stepRepository.findForCampaign(tenantId, c.getId());
        if (steps.isEmpty()) return 0;
        List<Step> rules = steps.stream().map(s -> new Step(s.getDayOffset(), s.getAction(), s.getText(), s.getActionUser())).toList();
        var live = members.findLive(tenantId, c.getId());
        Map<UUID, Customer> leads = new HashMap<>();
        if (!live.isEmpty()) customerRepository.findActiveByIds(tenantId, live.stream().map(CampaignMember::getCustomerId).toList()).forEach(l -> leads.put(l.getId(), l));
        int ran = 0;
        for (CampaignMember m : live) {
            Customer lead = leads.get(m.getCustomerId());
            if (lead == null || CampaignRules.exits(m.getStatus(), lead.getPipelineStage())) {
                m.exit();
                members.save(m);
                continue;
            }
            var due = CampaignRules.dueSteps(rules, m.getStepsDone(), m.getAddedAt().atZone(ZONE).toLocalDate(), today);
            for (int index : due) {
                if (ran >= budget) return ran;
                JourneyStep s = steps.get(index);
                boolean done = true;
                String detail;
                try {
                    detail = workflowService.perform(tenantId, s.getAction(), s.getText(), 0, s.getActionUser(), lead);
                } catch (Exception e) {
                    done = false;
                    detail = e.getMessage();
                    log.warn("[CRM] Journey step {} of campaign={} failed for lead={}: {}", s.getPosition(), c.getId(), lead.getId(), e.getMessage());
                }
                runRepository.save(JourneyRun.of(tenantId, c.getId(), lead.getId(), s.getPosition(), done, detail));
                m.stepsDone(index + 1);   // a failed step is not retried: it is in the log
                if (m.getStatus() == MemberStatus.ADDED) m.setStatus(MemberStatus.CONTACTED);
                ran++;
            }
            if (m.getStepsDone() >= steps.size()) m.exit();
            members.save(m);
        }
        return ran;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private Campaign require(TenantId tenantId, UUID id) {
        return campaigns.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Campaign", id.toString()));
    }

    private Map<UUID, String> names(TenantId tenantId, List<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (!ids.isEmpty()) customerRepository.findActiveByIds(tenantId, ids).forEach(c -> out.put(c.getId(), c.getName()));
        return out;
    }

    private CampaignResponse one(TenantId tenantId, Campaign c) {
        Map<MemberStatus, Integer> counts = new EnumMap<>(MemberStatus.class);
        members.findForCampaign(tenantId, c.getId()).forEach(m -> counts.merge(m.getStatus(), 1, Integer::sum));
        return toResponse(c, counts, stepRepository.findForCampaign(tenantId, c.getId()).size());
    }

    private static CampaignResponse toResponse(Campaign c, Map<MemberStatus, Integer> counts, int steps) {
        var s = CampaignRules.summarise(counts);
        return new CampaignResponse(c.getId(), c.getName(), c.getGoal(), c.getStatus(), c.getStartsOn(), c.getEndsOn(), steps,
                s.total(), s.contacted(), s.responded(), s.converted(), s.responseRate(), s.conversionRate());
    }

    private static JourneyStepResponse stepResponse(JourneyStep s, String userName) {
        return new JourneyStepResponse(s.getPosition(), s.getDayOffset(), s.getAction(), s.getText(), s.getActionUser(),
                CampaignRules.describeStep(new Step(s.getDayOffset(), s.getAction(), s.getText(), s.getActionUser()), userName));
    }
}

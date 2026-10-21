package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerActivity;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.SalesTarget;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.SalesTargetRepository;
import za.co.handyflow.platform.crm.dto.SetTargetRequest;
import za.co.handyflow.platform.crm.dto.TargetProgressRow;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Monthly sales targets, and how each salesperson's wins that month compare. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTargetService {

    /** Months run on South African time. */
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final SalesTargetRepository targetRepository;
    private final CustomerRepository customerRepository;
    private final CustomerActivityRepository activityRepository;
    private final UserRecipientResolver userResolver;

    @Transactional(readOnly = true)
    public List<TargetProgressRow> progress(TenantId tenantId, String period) {
        YearMonth month = parse(period);
        // When each lead was last marked won: the latest STAGE_CHANGED to WON on its timeline.
        Map<UUID, Instant> wonAt = new HashMap<>();
        for (CustomerActivity a : activityRepository.findStageChangesByTenant(tenantId)) {
            Object to = a.getPayload() != null ? a.getPayload().get("to") : null;
            if (to != null && LeadStage.WON.name().equals(to.toString())) wonAt.put(a.getCustomer().getId(), a.getCreatedAt());
        }
        List<TargetProgress.Win> wins = new ArrayList<>();
        for (var lead : customerRepository.findAllActiveLeads(tenantId)) {
            if (lead.getPipelineStage() != LeadStage.WON) continue;
            wins.add(new TargetProgress.Win(lead.getOwnerId(), lead.getDealValue(), wonAt.getOrDefault(lead.getId(), lead.getUpdatedAt())));
        }
        List<TargetProgress.Target> targets = targetRepository.findByPeriod(tenantId, month.toString()).stream()
                .map(t -> new TargetProgress.Target(t.getOwnerId(), t.getAmount())).toList();
        return TargetProgress.build(month, ZONE, wins, targets);
    }

    @Transactional
    public void set(TenantId tenantId, SetTargetRequest req) {
        YearMonth month = parse(req.period());
        if (userResolver.resolveUser(tenantId, req.ownerId()).isEmpty()) {
            throw new IllegalArgumentException("That person is not a user of this account");
        }
        UUID by = currentUserId();
        var existing = targetRepository.findOne(tenantId, req.ownerId(), month.toString());
        if (existing.isPresent()) {
            existing.get().changeAmount(req.amount(), by);
            targetRepository.save(existing.get());
        } else {
            targetRepository.save(SalesTarget.create(tenantId, req.ownerId(), month.toString(), req.amount(), by));
        }
        log.info("[CRM] Sales target set owner={} period={} tenant={}", req.ownerId(), month, tenantId);
    }

    /** Removing a target that is not there is harmless. */
    @Transactional
    public void clear(TenantId tenantId, UUID ownerId, String period) {
        YearMonth month = parse(period);
        targetRepository.findOne(tenantId, ownerId, month.toString()).ifPresent(targetRepository::delete);
    }

    static YearMonth parse(String period) {
        try {
            if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("bad", String.valueOf(period), 0);
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Period must look like 2026-11");
        }
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }
}

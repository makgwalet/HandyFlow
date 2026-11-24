package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.DeadlineGenerationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the due rules of the obligations that apply to a client into dated deadlines. Safe to run again: dates already there
 * are left alone, new ones are added, and pending ones that are no longer wanted are cancelled with a reason.
 */
@Service
@RequiredArgsConstructor
public class ClientDeadlineGenerationService {

    private static final int DEFAULT_MONTHS = 12;

    private final ComplianceClientRepository clients;
    private final ComplianceClientObligationRepository assignments;
    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ClientComplianceDeadlineRepository deadlines;

    @Transactional(readOnly = true)
    public KeyDatesView keyDates(TenantId tenantId, UUID clientId) {
        ComplianceClient c = requireClient(tenantId, clientId);
        return new KeyDatesView(c.getFinancialYearEndMonth(), c.getRegisteredOn());
    }

    @Transactional
    public KeyDatesView setKeyDates(TenantId tenantId, UUID clientId, KeyDatesRequest r, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        try {
            c.setKeyDates(r.financialYearEndMonth(), r.registeredOn(), today, by);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "KEY_DATES_INVALID");
        }
        clients.save(c);
        return new KeyDatesView(c.getFinancialYearEndMonth(), c.getRegisteredOn());
    }

    @Transactional
    public GenerationResult generate(TenantId tenantId, UUID clientId, Integer months, UUID by, LocalDate today) {
        ComplianceClient client = requireClient(tenantId, clientId);
        int span = months == null ? DEFAULT_MONTHS : months;
        if (span < 1 || span > DueDateRules.MAX_HORIZON_MONTHS) {
            throw new HandyFlowException("Look ahead 1 to " + DueDateRules.MAX_HORIZON_MONTHS + " months", HttpStatus.BAD_REQUEST, "DEADLINE_WINDOW_INVALID");
        }
        LocalDate to = today.plusMonths(span);
        List<ComplianceClientObligation> assigned = assignments.findForClient(tenantId, clientId);
        if (assigned.isEmpty()) {
            throw new HandyFlowException("No obligations are assigned to this client yet. Update the suggestions on the Obligations tab first",
                    HttpStatus.BAD_REQUEST, "DEADLINES_NO_OBLIGATIONS");
        }
        Map<UUID, ComplianceObligation> obById = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));
        Map<UUID, ComplianceObligationVersion> vById = versions.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligationVersion::getId, v -> v));
        Map<UUID, List<ClientComplianceDeadline>> existing = deadlines.findGeneratedForClient(tenantId, clientId).stream()
                .collect(Collectors.groupingBy(ClientComplianceDeadline::getObligationId));
        DueDateRules.Inputs inputs = new DueDateRules.Inputs(client.getFinancialYearEndMonth(), client.getRegisteredOn());

        int created = 0, cancelled = 0, unchanged = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (ComplianceClientObligation a : assigned) {
            ComplianceObligation o = obById.get(a.getObligationId());
            ComplianceObligationVersion v = vById.get(a.getVersionId());
            String code = o == null ? "?" : o.getCode();
            List<ClientComplianceDeadline> mine = existing.getOrDefault(a.getObligationId(), List.of());
            List<LocalDate> wanted = List.of();
            boolean decided = true;
            if (ApplicabilityRules.APPLICABLE.equals(a.effectiveState()) && v != null) {
                DueDateRules.Result r = DueDateRules.between(new DueDateRules.Rule(v.getDueRuleType(), v.getDueMonth(), v.getDueDay(), v.getDueOffsetDays(), v.getDueOffsetMonths()), inputs, today, to);
                if (r.reason() != null) {
                    decided = false;                       // nothing is cancelled when we simply cannot tell
                    if (!"NONE".equals(v.getDueRuleType())) skipped.add(new Skipped(code, r.reason()));
                } else {
                    wanted = r.dates();
                }
            } else if (ApplicabilityRules.PENDING.equals(a.effectiveState())) {
                decided = false;                           // not yet known whether it applies
            }
            if (!decided) continue;

            List<LocalDate> have = mine.stream().map(ClientComplianceDeadline::getDueDate).toList();
            List<LocalDate> pendingInWindow = mine.stream().filter(d -> "PENDING".equals(d.getStatus()) && !d.getDueDate().isBefore(today) && !d.getDueDate().isAfter(to))
                    .map(ClientComplianceDeadline::getDueDate).toList();
            String why = ApplicabilityRules.APPLICABLE.equals(a.effectiveState()) ? "The due rule changed, so this date no longer applies" : "This obligation no longer applies to the client";
            for (ClientComplianceDeadline d : mine) {
                if ("PENDING".equals(d.getStatus()) && DueDateRules.toCancel(pendingInWindow, wanted).contains(d.getDueDate())) {
                    d.cancel(why, by);
                    deadlines.save(d);
                    cancelled++;
                }
            }
            String text = code + (v == null || v.getName() == null ? "" : " - " + v.getName());
            for (LocalDate date : DueDateRules.toCreate(have, wanted)) {
                deadlines.save(ClientComplianceDeadline.generated(tenantId, clientId, a.getObligationId(), a.getVersionNumber(),
                        v == null ? null : v.getResponsibleRole(), text.length() > 500 ? text.substring(0, 500) : text, date, by));
                created++;
            }
            unchanged += (int) wanted.stream().filter(have::contains).count();
        }
        return new GenerationResult(today, to, created, cancelled, unchanged, skipped);
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }
}

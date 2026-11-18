package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractObligationDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Obligations (what a contract requires someone to do, and by when) and renewal decisions (what the business decided as a
 * signed contract nears its end). Both are recorded by staff. A renewal never extends a contract by itself: choosing RENEW
 * creates a successor draft that goes through review and signing like any other contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractObligationService {

    private final ContractRepository contractRepo;
    private final ContractPartyRepository partyRepo;
    private final ContractObligationRepository obligationRepo;
    private final ContractRenewalDecisionRepository decisionRepo;
    private final ContractNumberGenerator numberGen;

    // ── Obligations ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ObligationResponse> forContract(TenantId tenantId, UUID contractId) {
        Contract c = contract(tenantId, contractId);
        LocalDate today = LocalDate.now();
        return obligationRepo.findByTenantIdAndContractIdOrderByDueDateAsc(tenantId.getValue(), contractId).stream()
                .map(o -> toResponse(o, c, today)).toList();
    }

    /** Open obligations across the tenant; {@code dueWithinDays} null means all open ones, a value includes anything overdue. */
    @Transactional(readOnly = true)
    public List<ObligationResponse> open(TenantId tenantId, Integer dueWithinDays, boolean overdueOnly) {
        LocalDate today = LocalDate.now();
        LocalDate until = overdueOnly ? today.minusDays(1) : dueWithinDays == null ? null : today.plusDays(dueWithinDays);
        List<ContractObligation> list = obligationRepo.findOpen(tenantId.getValue(), until);
        Map<UUID, Contract> contracts = contractRepo.findAllById(list.stream().map(ContractObligation::getContractId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Contract::getId, Function.identity()));
        return list.stream().filter(o -> contracts.containsKey(o.getContractId()))
                .map(o -> toResponse(o, contracts.get(o.getContractId()), today)).toList();
    }

    @Transactional(readOnly = true)
    public ObligationSummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        UUID t = tenantId.getValue();
        return new ObligationSummary(obligationRepo.countOpen(t), obligationRepo.countOverdue(t, today),
                obligationRepo.countDueBetween(t, today, today.plusDays(7)));
    }

    @Transactional
    public ObligationResponse create(TenantId tenantId, UUID contractId, ObligationRequest req, UUID by) {
        Contract c = contract(tenantId, contractId);
        if (List.of("TERMINATED", "EXPIRED", "DECLINED", "CANCELLED").contains(c.getStatus()))
            throw new IllegalStateException("Obligations cannot be added to a " + c.getStatus().toLowerCase() + " contract");
        ContractObligation o = ContractObligation.create(tenantId.getValue(), contractId, req.title(), req.description(),
                req.obligationType(), req.responsibleSide(), req.ownerEmail(), req.dueDate(), req.recurrence(), by);
        obligationRepo.save(o);
        return toResponse(o, c, LocalDate.now());
    }

    @Transactional
    public ObligationResponse update(TenantId tenantId, UUID obligationId, ObligationRequest req) {
        ContractObligation o = obligation(tenantId, obligationId);
        o.edit(req.title(), req.description(), req.obligationType(), req.responsibleSide(), req.ownerEmail(), req.dueDate(), req.recurrence());
        obligationRepo.save(o);
        return toResponse(o, contract(tenantId, o.getContractId()), LocalDate.now());
    }

    @Transactional
    public ObligationResponse start(TenantId tenantId, UUID obligationId) {
        ContractObligation o = obligation(tenantId, obligationId);
        o.start();
        obligationRepo.save(o);
        return toResponse(o, contract(tenantId, o.getContractId()), LocalDate.now());
    }

    @Transactional
    public ObligationResponse complete(TenantId tenantId, UUID obligationId, String note, UUID by) {
        ContractObligation o = obligation(tenantId, obligationId);
        Optional<ContractObligation> next = o.complete(note, by);
        obligationRepo.save(o);
        next.ifPresent(obligationRepo::save);
        return toResponse(o, contract(tenantId, o.getContractId()), LocalDate.now());
    }

    @Transactional
    public ObligationResponse waive(TenantId tenantId, UUID obligationId, String reason) {
        ContractObligation o = obligation(tenantId, obligationId);
        o.waive(reason);
        obligationRepo.save(o);
        return toResponse(o, contract(tenantId, o.getContractId()), LocalDate.now());
    }

    // ── Renewals ─────────────────────────────────────────────────────────────

    /** Signed contracts ending within the window, with the date by which notice has to be given and whether a decision was made. */
    @Transactional(readOnly = true)
    public List<RenewalItem> renewals(TenantId tenantId, int withinDays) {
        LocalDate today = LocalDate.now();
        List<Contract> ending = new ArrayList<>(contractRepo.findSignedExpiringWithin(tenantId, today.minusDays(1), today.plusDays(withinDays)));
        // Auto-renewing contracts past their end date stay here until someone decides; they are not expired or extended automatically.
        Set<UUID> seen = ending.stream().map(Contract::getId).collect(Collectors.toSet());
        for (Contract c : contractRepo.findSignedAutoRenewPastEnd(tenantId, today)) if (seen.add(c.getId())) ending.add(c);
        if (ending.isEmpty()) return List.of();
        Map<UUID, ContractRenewalDecision> latest = new HashMap<>();
        for (ContractRenewalDecision d : decisionRepo.findByContracts(tenantId.getValue(), ending.stream().map(Contract::getId).toList()))
            latest.putIfAbsent(d.getContractId(), d);   // newest first
        return ending.stream().map(c -> renewalItem(c, latest.get(c.getId()), today))
                .sorted(Comparator.comparing(RenewalItem::endDate)).toList();
    }

    @Transactional(readOnly = true)
    public List<RenewalDecisionResponse> decisions(TenantId tenantId, UUID contractId) {
        contract(tenantId, contractId);
        return decisionRepo.findByTenantIdAndContractIdOrderByDecidedAtDesc(tenantId.getValue(), contractId).stream().map(this::toResponse).toList();
    }

    /**
     * Records a decision on a signed contract. RENEW also creates a successor DRAFT (same body, value and parties, new dates) that
     * must be reviewed and signed again; it needs the new end date. At most one successor can be created per contract.
     */
    @Transactional
    public RenewalDecisionResponse decide(TenantId tenantId, UUID contractId, RenewalDecisionRequest req, UUID by) {
        Contract c = contract(tenantId, contractId);
        if (!"SIGNED".equals(c.getStatus()))
            throw new IllegalStateException("A renewal decision can only be recorded on a signed contract (this one is " + c.getStatus() + ")");
        String decision = req.decision() == null ? "" : req.decision().trim().toUpperCase();
        UUID successorId = null;
        if ("RENEW".equals(decision)) {
            boolean already = decisionRepo.findByTenantIdAndContractIdOrderByDecidedAtDesc(tenantId.getValue(), contractId).stream()
                    .anyMatch(d -> d.getSuccessorContractId() != null);
            if (already) throw new IllegalStateException("A renewal draft was already created for this contract");
            if (req.newEndDate() == null) throw new IllegalArgumentException("A renewal needs the new end date");
            LocalDate start = req.newStartDate() != null ? req.newStartDate() : c.getEndDate() == null ? LocalDate.now() : c.getEndDate().plusDays(1);
            if (!req.newEndDate().isAfter(start)) throw new IllegalArgumentException("The new end date must be after the new start date");
            successorId = createSuccessor(tenantId, c, start, req.newEndDate(), by);
        }
        ContractRenewalDecision d = ContractRenewalDecision.record(tenantId.getValue(), contractId, decision, req.note(), by, successorId);
        decisionRepo.save(d);
        log.info("Renewal decision {} recorded for contract={} successor={}", decision, c.getContractNumber(), successorId);
        return toResponse(d);
    }

    private UUID createSuccessor(TenantId tenantId, Contract old, LocalDate start, LocalDate end, UUID by) {
        Contract next = Contract.create(tenantId, numberGen.next(tenantId), old.getTitle() + " (renewal)", old.getContractType(),
                old.getBody(), old.getTemplateId(), by);
        next.setDates(start, end);
        if (old.getValueAmount() != null) next.setValueAmount(old.getValueAmount());
        if (old.getNotes() != null) next.setNotes(old.getNotes());
        next.setAutoRenew(old.isAutoRenew(), old.getRenewalNoticeDays());
        if (old.getRequiredSigningMethod() != null) next.setRequiredSigningMethod(old.getRequiredSigningMethod());
        next.markRenewalOf(old.getId());
        contractRepo.save(next);
        int order = 1;
        for (ContractParty p : partyRepo.findByContract(old.getId())) {
            partyRepo.save(ContractParty.create(tenantId, next.getId(), p.getPartyType(), p.getPartyRole(), p.getFullName(), p.getEmail(),
                    p.getPhone(), p.getCompanyName(), p.getSigningOrder() > 0 ? p.getSigningOrder() : order));
            order++;
        }
        return next.getId();
    }

    static RenewalItem renewalItem(Contract c, ContractRenewalDecision latest, LocalDate today) {
        LocalDate deadline = c.getEndDate().minusDays(c.getRenewalNoticeDays());
        long toNotice = ChronoUnit.DAYS.between(today, deadline);
        String state = latest != null ? "DECIDED" : c.getEndDate().isBefore(today) ? "PAST_END" : toNotice < 0 ? "NOTICE_PASSED" : "DECISION_NEEDED";
        return new RenewalItem(c.getId(), c.getContractNumber(), c.getTitle(), c.getEndDate(), c.isAutoRenew(), c.getRenewalNoticeDays(),
                deadline, ChronoUnit.DAYS.between(today, c.getEndDate()), toNotice, state,
                latest == null ? null : latest.getDecision(), latest == null ? null : latest.getDecidedAt(),
                latest == null ? null : latest.getSuccessorContractId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Contract contract(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private ContractObligation obligation(TenantId tenantId, UUID id) {
        return obligationRepo.findByTenantIdAndId(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Obligation", id.toString()));
    }

    private ObligationResponse toResponse(ContractObligation o, Contract c, LocalDate today) {
        return new ObligationResponse(o.getId(), o.getContractId(), c.getContractNumber(), c.getTitle(), o.getTitle(), o.getDescription(),
                o.getObligationType(), o.getResponsibleSide(), o.getOwnerEmail(), o.getDueDate(), o.getRecurrence(), o.getStatus(),
                o.isOverdue(today), ChronoUnit.DAYS.between(today, o.getDueDate()), o.getCompletedAt(), o.getCompletionNote(), o.getWaivedReason());
    }

    private RenewalDecisionResponse toResponse(ContractRenewalDecision d) {
        return new RenewalDecisionResponse(d.getId(), d.getContractId(), d.getDecision(), d.getNote(), d.getDecidedAt(), d.getSuccessorContractId());
    }
}

package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.BodyDiff;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractClause;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseUsageRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.dto.ContractClauseDtos.*;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.ProposalView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The clause library. The wording comes from the tenant's legal reviewer: nothing is supplied. A clause is a draft until a
 * legal review of that exact wording is recorded, and only a published clause whose review has not lapsed can go into a contract.
 * Putting a clause into a contract is a change proposal, so it is reviewed like any other change to the text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractClauseService {

    private final ContractClauseRepository clauseRepo;
    private final ContractClauseUsageRepository usageRepo;
    private final ContractRepository contractRepo;
    private final ContractRedlineService redline;

    @Transactional(readOnly = true)
    public List<ClauseResponse> list(TenantId tenantId, String category, String status) {
        LocalDate today = LocalDate.now();
        return clauseRepo.search(tenantId.getValue(), blankToNull(category), blankToNull(status)).stream().map(c -> toResponse(c, today)).toList();
    }

    @Transactional(readOnly = true)
    public ClauseResponse get(TenantId tenantId, UUID id) { return toResponse(clause(tenantId, id), LocalDate.now()); }

    @Transactional
    public ClauseResponse create(TenantId tenantId, ClauseRequest req, UUID by) {
        ContractClause c = ContractClause.create(tenantId.getValue(), req.title(), req.category(), req.body(), req.legalArea(), req.jurisdiction(), by);
        return toResponse(clauseRepo.save(c), LocalDate.now());
    }

    @Transactional
    public ClauseResponse edit(TenantId tenantId, UUID id, ClauseRequest req) {
        ContractClause c = clause(tenantId, id);
        c.edit(req.title(), req.category(), req.body(), req.legalArea(), req.jurisdiction());
        return toResponse(clauseRepo.save(c), LocalDate.now());
    }

    @Transactional
    public ClauseResponse approve(TenantId tenantId, UUID id, ApproveClauseRequest req) {
        ContractClause c = clause(tenantId, id);
        c.approve(req.reviewerName(), req.reviewerProfessionalDetails(), req.reviewDate(), req.nextReviewDate(), req.notes(), LocalDate.now());
        return toResponse(clauseRepo.save(c), LocalDate.now());
    }

    @Transactional
    public ClauseResponse returnToDraft(TenantId tenantId, UUID id) {
        ContractClause c = clause(tenantId, id);
        c.returnToDraft();
        return toResponse(clauseRepo.save(c), LocalDate.now());
    }

    /** Publishes an approved clause. The previously published version of the same clause, if any, is superseded. */
    @Transactional
    public ClauseResponse publish(TenantId tenantId, UUID id) {
        ContractClause c = clause(tenantId, id);
        LocalDate today = LocalDate.now();
        clauseRepo.findFirstByTenantIdAndFamilyIdAndStatus(tenantId.getValue(), c.getFamilyId(), "PUBLISHED").ifPresent(prev -> {
            prev.supersede();
            clauseRepo.saveAndFlush(prev);
        });
        c.publish(today);
        return toResponse(clauseRepo.save(c), today);
    }

    @Transactional
    public ClauseResponse newVersion(TenantId tenantId, UUID id, UUID by) {
        ContractClause c = clause(tenantId, id);
        int next = clauseRepo.latestVersionNo(tenantId.getValue(), c.getFamilyId()) + 1;
        return toResponse(clauseRepo.save(c.newVersion(next, by)), LocalDate.now());
    }

    @Transactional
    public ClauseResponse withdraw(TenantId tenantId, UUID id, String reason) {
        ContractClause c = clause(tenantId, id);
        c.withdraw(reason);
        return toResponse(clauseRepo.save(c), LocalDate.now());
    }

    // ── Into a contract ──────────────────────────────────────────────────────

    /** Proposes the clause's wording as a change to the contract (a draft or one under review). The reviewer then accepts or rejects it. */
    @Transactional
    public ProposalView insert(TenantId tenantId, UUID contractId, InsertClauseRequest req, UUID by) {
        ContractClause c = clause(tenantId, req.clauseId());
        if (!c.usable(LocalDate.now()))
            throw new IllegalStateException(c.reviewExpired(LocalDate.now())
                    ? "The legal review of this clause has expired. It needs a new review before it can be used."
                    : "Only a published clause can be put into a contract (this one is " + c.getStatus() + ")");
        Contract contract = contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        String proposed = withClause(contract.getBody(), c.getBody(), req.afterBlock());
        String note = "Insert clause: " + c.getTitle() + " (version " + c.getVersionNo() + ")" + (req.note() == null || req.note().isBlank() ? "" : ". " + req.note());
        return redline.propose(tenantId, contractId, proposed, note, by, c.getId());
    }

    @Transactional(readOnly = true)
    public List<ClauseUsageResponse> usage(TenantId tenantId, UUID contractId) {
        contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        LocalDate today = LocalDate.now();
        List<ClauseUsageResponse> out = new ArrayList<>();
        usageRepo.findByTenantIdAndContractIdOrderByInsertedAtDesc(tenantId.getValue(), contractId).forEach(u ->
                clauseRepo.findByTenantIdAndId(tenantId.getValue(), u.getClauseId()).ifPresent(c ->
                        out.add(new ClauseUsageResponse(c.getId(), c.getTitle(), u.getClauseVersionNo(), usageState(c, today), u.getContractVersionNo(), u.getInsertedAt()))));
        return out;
    }

    /** Inserts the clause blocks into the body. afterBlock null = end, -1 = start, n = after block n (clamped to the body). */
    static String withClause(String currentBody, String clauseBody, Integer afterBlock) {
        List<String> blocks = new ArrayList<>(BodyDiff.blocks(currentBody));
        int at = afterBlock == null ? blocks.size() : Math.max(0, Math.min(blocks.size(), afterBlock + 1));
        blocks.addAll(at, BodyDiff.blocks(clauseBody));
        return String.join("", blocks);
    }

    static String usageState(ContractClause c, LocalDate today) {
        return switch (c.getStatus()) {
            case "SUPERSEDED" -> "SUPERSEDED";
            case "WITHDRAWN" -> "WITHDRAWN";
            default -> c.reviewExpired(today) ? "REVIEW_EXPIRED" : "CURRENT";
        };
    }

    static String legalStatus(ContractClause c, LocalDate today) {
        return switch (c.getStatus()) {
            case "DRAFT" -> "NOT_REVIEWED";
            case "SUPERSEDED" -> "SUPERSEDED";
            case "WITHDRAWN" -> "WITHDRAWN";
            default -> c.reviewExpired(today) ? "REVIEW_EXPIRED" : "REVIEWED";
        };
    }

    private ClauseResponse toResponse(ContractClause c, LocalDate today) {
        return new ClauseResponse(c.getId(), c.getFamilyId(), c.getVersionNo(), c.getTitle(), c.getCategory(), c.getBody(), c.getLegalArea(), c.getJurisdiction(),
                c.getStatus(), legalStatus(c, today), c.getReviewerName(), c.getReviewerProfessionalDetails(), c.getReviewDate(), c.getNextReviewDate(),
                c.getReviewNotes(), c.getPublishedAt(), c.getWithdrawnReason(), c.usable(today));
    }

    private ContractClause clause(TenantId tenantId, UUID id) {
        return clauseRepo.findByTenantIdAndId(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Clause", id.toString()));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim().toUpperCase(); }
}

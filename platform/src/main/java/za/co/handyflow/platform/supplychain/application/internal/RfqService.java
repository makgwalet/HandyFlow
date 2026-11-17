package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RfqStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RFQ lifecycle: DRAFT -> publish (invitations e-mailed) -> OPEN -> CLOSED -> AWARDED -> purchase order.
 * <ul>
 *  <li>Competitive by default: at least app.scm.rfq.min-invitations (default 2) suppliers must be invited, otherwise
 *      a written sole-source justification is required to publish.</li>
 *  <li>Invitations go only to ACTIVE suppliers that meet the document policy (chunk 5). The e-mail contains the
 *      specification and closing time — never the estimates or other suppliers.</li>
 *  <li>The award always records WHY. A reason of real substance (15+ characters) is required whenever the winner
 *      is not both the highest-scoring and the lowest-priced valid quotation.</li>
 *  <li>Creating the PO uses ScmService (supplier-orderable rule, numbering, VAT) and, when the RFQ came from a
 *      requisition, records the conversions so requisition fulfilment tracks automatically.</li>
 * </ul>
 * app.scm.sod.enforce=true: whoever created the RFQ cannot award it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final ScRfqRepository rfqRepo;
    private final ScRfqLineRepository lineRepo;
    private final ScRfqInvitationRepository inviteRepo;
    private final ScQuotationRepository quoteRepo;
    private final ScRfqEventRepository eventRepo;
    private final ScSupplierRepository supplierRepo;
    private final ScStockLocationRepository locationRepo;
    private final ScRequisitionRepository requisitionRepo;
    private final ScRequisitionLineRepository requisitionLineRepo;
    private final ScRequisitionConversionRepository conversionRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final SupplierComplianceService compliance;
    private final RfqComparisonService comparison;
    private final ScmService scmService;
    private final TenantSequenceService sequenceService;
    private final EmailService emailService;
    private final VatRateProvider vatRateProvider;

    @Value("${app.scm.rfq.min-invitations:2}")
    private int minInvitations;

    @Value("${app.scm.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    private record Seed(UUID requisitionLineId, UUID catalogueItemId, String name, String description, BigDecimal qty, BigDecimal est) {}

    // ── Create / edit ─────────────────────────────────────────────────────────

    @Transactional
    public RfqResponse create(TenantId tenantId, UUID userId, String userName, CreateRfqRequest req) {
        UUID tid = tenantId.getValue();
        requireTitle(req);
        int[] weights = weights(req.weights());
        checkLocation(tid, req.deliverToLocation());
        List<Seed> seeds = resolveLines(tenantId, req.requisitionId(), req.lines());
        List<ScSupplier> suppliers = resolveSuppliers(tenantId, req.supplierIds());

        String number = "RFQ-" + String.format("%05d", sequenceService.nextValue(tenantId, "RFQ"));
        ScRfq rfq = rfqRepo.save(ScRfq.create(tid, number, req.title().trim(), trim(req.description()), req.requisitionId(),
                req.deliverToLocation(), req.requiredByDate(), req.closesAt(), req.currency(), weights, userId, userName));
        saveLines(rfq, seeds);
        saveInvitations(rfq, suppliers);
        event(tid, rfq.getId(), "CREATED", number + (req.requisitionId() != null ? " from requisition" : ""), userId, userName);
        return detail(rfq);
    }

    @Transactional
    public RfqResponse update(TenantId tenantId, UUID id, UUID userId, String userName, CreateRfqRequest req) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (rfq.getStatus() != RfqStatus.DRAFT) throw conflict("Only a DRAFT RFQ can be edited — this one is " + rfq.getStatus());
        requireTitle(req);
        int[] weights = weights(req.weights());
        checkLocation(tid, req.deliverToLocation());
        List<Seed> seeds = resolveLines(tenantId, rfq.getRequisitionId(), req.lines());
        List<ScSupplier> suppliers = resolveSuppliers(tenantId, req.supplierIds());

        rfq.edit(req.title().trim(), trim(req.description()), req.deliverToLocation(), req.requiredByDate(), req.closesAt(), req.currency(), weights);
        lineRepo.deleteByRfq(tid, id);
        inviteRepo.deleteByRfq(tid, id);
        saveLines(rfq, seeds);
        saveInvitations(rfq, suppliers);
        rfqRepo.save(rfq);
        return detail(rfq);
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Transactional
    public RfqResponse publish(TenantId tenantId, UUID id, UUID userId, String userName, String soleSourceJustification) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        List<ScRfqLine> lines = lineRepo.findByRfq(tid, id);
        List<ScRfqInvitation> invites = inviteRepo.findByRfq(tid, id);
        if (lines.isEmpty()) throw bad("Add at least one line before publishing");
        if (invites.isEmpty()) throw bad("Invite at least one supplier before publishing");
        if (rfq.getClosesAt() == null || !rfq.getClosesAt().isAfter(Instant.now()))
            throw bad("Set a closing time in the future before publishing");
        String justification = trim(soleSourceJustification);
        if (invites.size() < minInvitations && justification == null)
            throw new HandyFlowException("Only " + invites.size() + " supplier(s) invited — policy requires at least " + minInvitations
                    + ". Invite more, or give a written sole-source justification.", HttpStatus.BAD_REQUEST, "SOLE_SOURCE_JUSTIFICATION_REQUIRED");

        rfq.publish(invites.size() < minInvitations ? justification : null);
        rfqRepo.save(rfq);
        for (ScRfqInvitation i : invites) {
            if (i.getContactEmail() != null && !i.getContactEmail().isBlank()) { i.markEmailed(); inviteRepo.save(i); }
        }
        event(tid, id, "PUBLISHED", invites.size() + " supplier(s) invited, closes " + WHEN.format(rfq.getClosesAt())
                + (justification != null && invites.size() < minInvitations ? " — sole source: " + justification : ""), userId, userName);
        sendInvitations(rfq, lines, invites, userName);
        return detail(rfq);
    }

    @Transactional
    public RfqResponse extend(TenantId tenantId, UUID id, Instant closesAt, String reason, UUID userId, String userName) {
        if (reason == null || reason.isBlank()) throw new HandyFlowException("A reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        if (closesAt == null || !closesAt.isAfter(Instant.now())) throw bad("The new closing time must be in the future");
        ScRfq rfq = rfqRepo.lockByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        Instant old = rfq.getClosesAt();
        rfq.reopen(closesAt);
        rfqRepo.save(rfq);
        event(tenantId.getValue(), id, "EXTENDED", (old != null ? WHEN.format(old) : "none") + " -> " + WHEN.format(closesAt) + ": " + reason.trim(), userId, userName);
        return detail(rfq);
    }

    @Transactional
    public RfqResponse close(TenantId tenantId, UUID id, UUID userId, String userName) {
        ScRfq rfq = rfqRepo.lockByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        rfq.close();
        rfqRepo.save(rfq);
        event(tenantId.getValue(), id, "CLOSED", quoteRepo.countSubmitted(tenantId.getValue(), id) + " quotation(s) received", userId, userName);
        return detail(rfq);
    }

    @Transactional
    public RfqResponse cancel(TenantId tenantId, UUID id, String reason, UUID userId, String userName) {
        if (reason == null || reason.isBlank()) throw new HandyFlowException("A cancellation reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        ScRfq rfq = rfqRepo.lockByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        rfq.cancel(reason.trim());
        rfqRepo.save(rfq);
        event(tenantId.getValue(), id, "CANCELLED", reason.trim(), userId, userName);
        return detail(rfq);
    }

    @Transactional
    public RfqResponse declineInvitation(TenantId tenantId, UUID id, UUID supplierId, String reason, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (rfq.getStatus() != RfqStatus.OPEN && rfq.getStatus() != RfqStatus.CLOSED)
            throw conflict("Declines can only be recorded while the RFQ is OPEN or CLOSED");
        ScRfqInvitation i = inviteRepo.findByRfqAndSupplier(tid, id, supplierId).orElseThrow(() -> bad("That supplier was not invited"));
        i.decline(reason);
        inviteRepo.save(i);
        event(tid, id, "INVITATION_DECLINED", i.getSupplierName() + (reason != null ? ": " + reason : ""), userId, userName);
        return detail(rfq);
    }

    // ── Award ─────────────────────────────────────────────────────────────────

    @Transactional
    public RfqResponse award(TenantId tenantId, UUID id, UUID quotationId, String reason, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("The reason for the selection must be recorded", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (rfq.getStatus() != RfqStatus.CLOSED)
            throw conflict("Only a CLOSED RFQ can be awarded — this one is " + rfq.getStatus() + (rfq.getStatus() == RfqStatus.OPEN ? " (close it first)" : ""));
        if (enforceSegregationOfDuties && rfq.getCreatedBy() != null && rfq.getCreatedBy().equals(userId))
            throw new HandyFlowException("Segregation of duties: you cannot award an RFQ you created yourself", HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");

        ScQuotation q = quoteRepo.findByTenantAndId(tid, quotationId).orElseThrow(() -> bad("Quotation not found"));
        if (!q.getRfqId().equals(id)) throw bad("Quotation does not belong to this RFQ");
        if (q.getStatus() != QuotationStatus.SUBMITTED) throw conflict("A " + q.getStatus() + " quotation cannot be awarded");
        if (q.getValidUntil() != null && q.getValidUntil().isBefore(LocalDate.now()))
            throw new HandyFlowException("That quotation's validity ended on " + q.getValidUntil()
                    + " — get a revised quotation (extend the RFQ and re-submit it) first", HttpStatus.CONFLICT, "QUOTE_EXPIRED");
        ScSupplier supplier = supplierRepo.findByTenantIdAndId(tid, q.getSupplierId())
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!supplier.isOrderable())
            throw conflict(supplier.getName() + " is " + supplier.getStatus() + " and cannot be awarded business");
        // Documents can lapse between invitation and award — re-apply the same compliance policy.
        compliance.requireCompliant(tenantId, q.getSupplierId());

        RfqComparisonResponse cmp = comparison.compare(tenantId, id, false);
        boolean isRecommended = quotationId.equals(cmp.recommendedQuotationId());
        boolean isLowest = quotationId.equals(cmp.lowestPriceQuotationId());
        boolean overrides = !(isRecommended && isLowest);
        if (overrides && reason.trim().length() < 15)
            throw new HandyFlowException("This is not both the highest-scoring and the lowest-priced quotation — explain the selection "
                    + "(at least 15 characters)", HttpStatus.BAD_REQUEST, "AWARD_JUSTIFICATION_REQUIRED");

        rfq.award(quotationId, reason.trim(), overrides, userId, userName);
        rfqRepo.save(rfq);
        event(tid, id, "AWARDED", q.getSupplierName() + " at landed R " + q.getLandedCost()
                + (overrides ? " [differs from recommendation: " + (isRecommended ? "" : "not top-scored ") + (isLowest ? "" : "not lowest price") + "]" : "")
                + ": " + reason.trim(), userId, userName);
        log.info("[SCM] RFQ {} awarded to {} (overrides recommendation: {})", rfq.getRfqNumber(), q.getSupplierName(), overrides);
        return detail(rfq);
    }

    // ── Purchase order ────────────────────────────────────────────────────────

    @Transactional
    public RfqResponse createPo(TenantId tenantId, UUID id, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (rfq.getStatus() != RfqStatus.AWARDED) throw conflict("Only an AWARDED RFQ can produce a purchase order — this one is " + rfq.getStatus());
        if (rfq.getPoId() != null) throw conflict("A purchase order has already been created from this RFQ");

        ScQuotation q = quoteRepo.findByTenantAndId(tid, rfq.getAwardedQuotationId()).orElseThrow(this::notFound);
        Map<UUID, ScRfqLine> rfqLines = new LinkedHashMap<>();
        lineRepo.findByRfq(tid, id).forEach(l -> rfqLines.put(l.getId(), l));

        record Pick(ScRfqLine rfqLine, ScQuotationLine quoteLine, BigDecimal qty) {}
        List<Pick> picks = new ArrayList<>();
        for (ScQuotationLine ql : quoteLineRepoLines(tid, q.getId())) {
            if (ql.isNoBid()) continue;
            ScRfqLine rl = rfqLines.get(ql.getRfqLineId());
            if (rl != null) picks.add(new Pick(rl, ql, ql.getQtyOffered().min(rl.getQuantity())));
        }
        if (picks.isEmpty()) throw conflict("The awarded quotation has no priced lines");

        ScRequisition requisition = null;
        Map<UUID, BigDecimal> ordered = new HashMap<>();
        Map<UUID, ScRequisitionLine> reqLines = new HashMap<>();
        if (rfq.getRequisitionId() != null) {
            // Locked: a concurrent convertToPo on the same requisition would otherwise over-order its lines.
            requisition = requisitionRepo.lockByTenantAndId(tid, rfq.getRequisitionId()).orElse(null);
            if (requisition == null || requisition.getStatus() != RequisitionStatus.APPROVED)
                throw conflict("The requisition this RFQ came from is no longer APPROVED");
            requisitionLineRepo.findByRequisition(tid, requisition.getId()).forEach(l -> reqLines.put(l.getId(), l));
            for (Object[] row : conversionRepo.sumActiveByLine(tid, requisition.getId(), PoStatus.CANCELLED))
                ordered.put((UUID) row[0], (BigDecimal) row[1]);
            for (Pick p : picks) {
                ScRequisitionLine rl = p.rfqLine().getRequisitionLineId() == null ? null : reqLines.get(p.rfqLine().getRequisitionLineId());
                if (rl == null) continue;
                BigDecimal remaining = rl.getQtyRequested().subtract(ordered.getOrDefault(rl.getId(), BigDecimal.ZERO));
                if (p.qty().compareTo(remaining) > 0)
                    throw conflict(rl.getItemName() + ": the requisition now has only " + plain(remaining) + " left to order (RFQ awarded " + plain(p.qty()) + ")");
            }
        }

        ScPurchaseOrder po = scmService.createPurchaseOrder(tenantId, userId, userName, new CreatePurchaseOrderRequest(
                q.getSupplierId(), rfq.getDeliverToLocation(), LocalDate.now(), rfq.getRequiredByDate(), rfq.getCurrency(),
                requisition != null ? requisition.getProjectRef() : null,
                "Ref " + rfq.getRfqNumber() + (q.getQuoteReference() != null ? " / quotation " + q.getQuoteReference() : ""),
                "Awarded via " + rfq.getRfqNumber() + ": " + rfq.getAwardReason()));
        UUID poId = po.getId();

        for (Pick p : picks) {
            scmService.addPurchaseOrderLine(tenantId, poId, new AddPoLineRequest(p.rfqLine().getItemName(), null, p.qty(),
                    p.quoteLine().netUnitPrice(), p.quoteLine().getVatRate(), p.rfqLine().getCatalogueItemId()));
        }
        BigDecimal stdVat = vatRateProvider.ratePercent();
        if (q.getDeliveryCost().signum() > 0)
            scmService.addPurchaseOrderLine(tenantId, poId, new AddPoLineRequest("Delivery / freight (" + rfq.getRfqNumber() + ")",
                    null, BigDecimal.ONE, q.getDeliveryCost(), stdVat, null));
        if (q.getOtherCharges().signum() > 0)
            scmService.addPurchaseOrderLine(tenantId, poId, new AddPoLineRequest("Other charges (" + rfq.getRfqNumber() + ")",
                    null, BigDecimal.ONE, q.getOtherCharges(), stdVat, null));

        if (requisition != null) {
            ScPurchaseOrder linked = poRepo.findByTenantIdAndId(tid, poId).orElseThrow();
            linked.linkRequisition(requisition.getId());
            poRepo.save(linked);
            for (Pick p : picks) {
                UUID rlId = p.rfqLine().getRequisitionLineId();
                if (rlId != null && reqLines.containsKey(rlId))
                    conversionRepo.save(ScRequisitionConversion.create(tid, requisition.getId(), rlId, poId, p.qty(),
                            p.quoteLine().netUnitPrice(), userId, userName));
            }
        }
        rfq.linkPo(poId);
        rfqRepo.save(rfq);
        event(tid, id, "PO_CREATED", "Draft purchase order raised for " + q.getSupplierName(), userId, userName);
        return detail(rfq);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RfqResponse get(TenantId tenantId, UUID id) {
        return detail(rfqRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public Page<RfqResponse> list(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        Page<ScRfq> page;
        if (status != null && !status.isBlank()) {
            try { page = rfqRepo.findByStatus(tid, RfqStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)), pageable); }
            catch (IllegalArgumentException e) { throw bad("Unknown status '" + status + "'"); }
        } else {
            page = rfqRepo.findAllForTenant(tid, pageable);
        }
        return page.map(r -> toResponse(r, List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public List<RfqEventResponse> events(TenantId tenantId, UUID id) {
        rfqRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        return eventRepo.findAllForRfq(tenantId.getValue(), id).stream()
                .map(e -> new RfqEventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorName(), e.getCreatedAt())).toList();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private List<ScQuotationLine> quoteLineRepoLines(UUID tid, UUID quotationId) {
        return quotationLines.findByQuotation(tid, quotationId);
    }

    private final ScQuotationLineRepository quotationLines;

    private RfqResponse detail(ScRfq rfq) {
        UUID tid = rfq.getTenantId();
        List<RfqResponse.Line> lines = lineRepo.findByRfq(tid, rfq.getId()).stream()
                .map(l -> new RfqResponse.Line(l.getId(), l.getLineNumber(), l.getRequisitionLineId(), l.getCatalogueItemId(),
                        l.getItemName(), l.getDescription(), l.getQuantity(), l.getEstimatedUnitCost())).toList();
        List<RfqResponse.Invitation> invites = inviteRepo.findByRfq(tid, rfq.getId()).stream()
                .map(i -> new RfqResponse.Invitation(i.getSupplierId(), i.getSupplierName(), i.getStatus(), i.isEmailed(),
                        i.getDeclineReason(), i.getInvitedAt(), i.getRespondedAt())).toList();
        return toResponse(rfq, lines, invites);
    }

    private RfqResponse toResponse(ScRfq r, List<RfqResponse.Line> lines, List<RfqResponse.Invitation> invites) {
        BigDecimal est = BigDecimal.ZERO;
        boolean any = false;
        for (RfqResponse.Line l : lines) {
            if (l.estimatedUnitCost() != null) { est = est.add(l.estimatedUnitCost().multiply(l.quantity())); any = true; }
        }
        return new RfqResponse(r.getId(), r.getRfqNumber(), r.getStatus().name(), r.getTitle(), r.getDescription(),
                r.getRequisitionId(), r.getDeliverToLocation(), r.getRequiredByDate(), r.getClosesAt(), r.getCurrency(),
                new RfqResponse.Weights(r.getWeightPrice(), r.getWeightLeadTime(), r.getWeightTechnical(), r.getWeightPerformance(), r.getWeightBbbee()),
                r.getSoleSourceJustification(), any ? est.setScale(2, RoundingMode.HALF_UP) : null,
                (int) quoteRepo.countSubmitted(r.getTenantId(), r.getId()), r.getAwardedQuotationId(), r.getAwardReason(),
                r.isOverridesRecommendation(), r.getAwardedByName(), r.getAwardedAt(), r.getPoId(), r.getCreatedByName(),
                r.getCreatedAt(), lines, invites);
    }

    private List<Seed> resolveLines(TenantId tenantId, UUID requisitionId, List<CreateRfqRequest.Line> reqLines) {
        UUID tid = tenantId.getValue();
        List<Seed> out = new ArrayList<>();
        if (requisitionId != null) {
            ScRequisition r = requisitionRepo.findByTenantAndId(tid, requisitionId)
                    .orElseThrow(() -> new HandyFlowException("Requisition not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            if (r.getStatus() != RequisitionStatus.APPROVED)
                throw conflict("An RFQ can only be raised from an APPROVED requisition — " + r.getRequisitionNumber() + " is " + r.getStatus());
            Map<UUID, ScRequisitionLine> lines = new LinkedHashMap<>();
            requisitionLineRepo.findByRequisition(tid, requisitionId).forEach(l -> lines.put(l.getId(), l));
            Map<UUID, BigDecimal> ordered = new HashMap<>();
            for (Object[] row : conversionRepo.sumActiveByLine(tid, requisitionId, PoStatus.CANCELLED)) ordered.put((UUID) row[0], (BigDecimal) row[1]);

            if (reqLines == null || reqLines.isEmpty()) {
                for (ScRequisitionLine l : lines.values()) {
                    BigDecimal remaining = l.getQtyRequested().subtract(ordered.getOrDefault(l.getId(), BigDecimal.ZERO));
                    if (remaining.signum() > 0)
                        out.add(new Seed(l.getId(), l.getCatalogueItemId(), l.getItemName(), l.getDescription(), remaining, l.getEstimatedUnitCost()));
                }
            } else {
                for (CreateRfqRequest.Line c : reqLines) {
                    ScRequisitionLine l = c.requisitionLineId() == null ? null : lines.get(c.requisitionLineId());
                    if (l == null) throw bad("Line " + c.requisitionLineId() + " is not on requisition " + r.getRequisitionNumber());
                    BigDecimal remaining = l.getQtyRequested().subtract(ordered.getOrDefault(l.getId(), BigDecimal.ZERO));
                    BigDecimal qty = c.quantity() != null ? c.quantity() : remaining;
                    if (qty.signum() <= 0 || qty.compareTo(remaining) > 0)
                        throw bad(l.getItemName() + ": quantity must be between 0 and the " + plain(remaining) + " still to be ordered");
                    out.add(new Seed(l.getId(), l.getCatalogueItemId(), l.getItemName(), l.getDescription(), qty, l.getEstimatedUnitCost()));
                }
            }
            if (out.isEmpty()) throw bad("Nothing is left to source on requisition " + r.getRequisitionNumber());
            return out;
        }
        if (reqLines == null) return out;
        for (CreateRfqRequest.Line c : reqLines) {
            if (c.itemName() == null || c.itemName().isBlank()) throw bad("Every line needs an item name");
            if (c.quantity() == null || c.quantity().signum() <= 0) throw bad(c.itemName() + ": quantity must be greater than zero");
            out.add(new Seed(null, c.catalogueItemId(), c.itemName().trim(), trim(c.description()), c.quantity(), c.estimatedUnitCost()));
        }
        return out;
    }

    private List<ScSupplier> resolveSuppliers(TenantId tenantId, List<UUID> ids) {
        List<ScSupplier> out = new ArrayList<>();
        if (ids == null) return out;
        for (UUID sid : new LinkedHashSet<>(ids)) {
            ScSupplier s = supplierRepo.findByTenantIdAndId(tenantId.getValue(), sid)
                    .orElseThrow(() -> new HandyFlowException("Supplier " + sid + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            if (!s.isOrderable()) throw bad(s.getName() + " is " + s.getStatus() + " and cannot be invited");
            compliance.requireCompliant(tenantId, sid);
            out.add(s);
        }
        return out;
    }

    private void saveLines(ScRfq rfq, List<Seed> seeds) {
        int n = 1;
        for (Seed s : seeds)
            lineRepo.save(ScRfqLine.create(rfq.getTenantId(), rfq.getId(), n++, s.requisitionLineId(), s.catalogueItemId(),
                    s.name(), s.description(), s.qty(), s.est()));
    }

    private void saveInvitations(ScRfq rfq, List<ScSupplier> suppliers) {
        for (ScSupplier s : suppliers)
            inviteRepo.save(ScRfqInvitation.create(rfq.getTenantId(), rfq.getId(), s.getId(), s.getName(), s.getContactEmail()));
    }

    /** Specification only — no estimates, no other suppliers. Sent after commit; a mail failure never blocks publishing. */
    private void sendInvitations(ScRfq rfq, List<ScRfqLine> lines, List<ScRfqInvitation> invites, String buyer) {
        StringBuilder rows = new StringBuilder();
        for (ScRfqLine l : lines) {
            rows.append("<tr><td>").append(HtmlUtils.htmlEscape(l.getItemName())).append("</td><td>")
                    .append(l.getDescription() != null ? HtmlUtils.htmlEscape(l.getDescription()) : "").append("</td><td style='text-align:right'>")
                    .append(plain(l.getQuantity())).append("</td></tr>");
        }
        String html = "<p>You are invited to quote for <strong>" + HtmlUtils.htmlEscape(rfq.getTitle()) + "</strong> (" + rfq.getRfqNumber() + ").</p>"
                + (rfq.getDescription() != null ? "<p>" + HtmlUtils.htmlEscape(rfq.getDescription()) + "</p>" : "")
                + "<table border='1' cellpadding='4' cellspacing='0'><tr><th>Item</th><th>Details</th><th>Qty</th></tr>" + rows + "</table>"
                + "<p><strong>Closing:</strong> " + WHEN.format(rfq.getClosesAt()) + (rfq.getRequiredByDate() != null ? " &middot; <strong>Required by:</strong> " + rfq.getRequiredByDate() : "") + "</p>"
                + "<p>Please reply with your unit prices, any discount, delivery cost, lead time, warranty and quotation validity, quoting "
                + rfq.getRfqNumber() + ". Contact: " + HtmlUtils.htmlEscape(buyer) + ".</p>";
        String subject = "Request for quotation " + rfq.getRfqNumber() + " — " + rfq.getTitle();
        List<String> recipients = invites.stream().map(ScRfqInvitation::getContactEmail).filter(e -> e != null && !e.isBlank()).toList();
        Runnable send = () -> recipients.forEach(to -> {
            try { emailService.send(to, subject, html); } catch (Exception e) { log.warn("[SCM] RFQ invitation e-mail to {} failed: {}", to, e.getMessage()); }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private int[] weights(CreateRfqRequest.Weights w) {
        if (w == null) return new int[]{50, 15, 15, 10, 10};
        int[] a = {nz(w.price()), nz(w.leadTime()), nz(w.technical()), nz(w.performance()), nz(w.bbbee())};
        if (!RfqEvaluationRules.validWeights(new RfqEvaluationRules.Weights(a[0], a[1], a[2], a[3], a[4])))
            throw bad("Criteria weights must be zero or more and total exactly 100");
        return a;
    }

    private void requireTitle(CreateRfqRequest req) {
        if (req.title() == null || req.title().isBlank()) throw bad("A title is required");
    }

    private void checkLocation(UUID tid, UUID locationId) {
        if (locationId != null)
            locationRepo.findActiveByTenantIdAndId(tid, locationId)
                    .orElseThrow(() -> new HandyFlowException("Delivery location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private void event(UUID tid, UUID rfqId, String type, String detail, UUID actor, String actorName) {
        eventRepo.save(ScRfqEvent.of(tid, rfqId, type, detail, actor, actorName));
    }

    private HandyFlowException notFound() { return new HandyFlowException("RFQ not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
    private HandyFlowException conflict(String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, "INVALID_RFQ_STATE"); }
    private HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "INVALID_RFQ"); }
    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
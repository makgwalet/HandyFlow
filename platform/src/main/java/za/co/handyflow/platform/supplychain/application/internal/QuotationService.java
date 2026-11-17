package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RfqStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.QuotationRequest;
import za.co.handyflow.platform.supplychain.dto.QuotationResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Captures supplier quotations (entered by the buyer for now — there is no supplier portal yet), technical
 * scoring, disqualification and quote documents. One quotation per invited supplier; submitting again REVISES it
 * (the previous landed cost is written to the RFQ audit trail and the technical score is reset, because the
 * offer changed). Quotes are only accepted while the RFQ is OPEN and before its closing time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationService {

    private final ScRfqRepository rfqRepo;
    private final ScRfqLineRepository rfqLineRepo;
    private final ScRfqInvitationRepository inviteRepo;
    private final ScQuotationRepository quoteRepo;
    private final ScQuotationLineRepository quoteLineRepo;
    private final ScRfqEventRepository eventRepo;
    private final RfqComparisonService comparison;
    private final EvidenceFacade evidenceFacade;
    private final VatRateProvider vatRateProvider;

    // ── Capture ───────────────────────────────────────────────────────────────

    @Transactional
    public QuotationResponse submit(TenantId tenantId, UUID rfqId, UUID userId, String userName, QuotationRequest req) {
        UUID tid = tenantId.getValue();
        ScRfq rfq = rfqRepo.lockByTenantAndId(tid, rfqId).orElseThrow(this::rfqNotFound);
        if (!rfq.isOpenForQuotes(Instant.now()))
            throw conflict("RFQ " + rfq.getRfqNumber() + " is not accepting quotations (status " + rfq.getStatus()
                    + (rfq.getStatus() == RfqStatus.OPEN ? ", closing time passed" : "") + ") — extend it first if a late quote must be accepted");
        if (req.supplierId() == null) throw bad("supplierId is required");
        ScRfqInvitation invitation = inviteRepo.findByRfqAndSupplier(tid, rfqId, req.supplierId())
                .orElseThrow(() -> bad("That supplier was not invited to this RFQ"));
        if (req.leadTimeDays() == null || req.leadTimeDays() < 0) throw bad("leadTimeDays is required");
        if (req.validUntil() != null && req.validUntil().isBefore(LocalDate.now())) throw bad("The quotation's validity date has already passed");
        BigDecimal delivery = nz(req.deliveryCost());
        BigDecimal other = nz(req.otherCharges());
        if (delivery.signum() < 0 || other.signum() < 0) throw bad("Delivery and other charges cannot be negative");

        Map<UUID, ScRfqLine> rfqLines = new LinkedHashMap<>();
        rfqLineRepo.findByRfq(tid, rfqId).forEach(l -> rfqLines.put(l.getId(), l));
        ScQuotation existing = quoteRepo.findByRfqAndSupplier(tid, rfqId, req.supplierId()).orElse(null);
        if (existing != null && existing.getStatus() == QuotationStatus.DISQUALIFIED)
            throw conflict("This supplier's quotation was disqualified and cannot be revised");
        UUID quotationId = existing != null ? existing.getId() : UUID.randomUUID();

        List<ScQuotationLine> lines = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO, vat = BigDecimal.ZERO;
        boolean anyPriced = false;
        for (QuotationRequest.Line l : req.lines() == null ? List.<QuotationRequest.Line>of() : req.lines()) {
            ScRfqLine rl = l.rfqLineId() == null ? null : rfqLines.get(l.rfqLineId());
            if (rl == null) throw bad("Line " + l.rfqLineId() + " is not on this RFQ");
            if (!seen.add(rl.getId())) throw bad(rl.getItemName() + " appears twice");
            BigDecimal rate = l.vatRate() != null ? l.vatRate() : vatRateProvider.ratePercent();
            if (Boolean.TRUE.equals(l.noBid())) {
                lines.add(ScQuotationLine.noBid(tid, quotationId, rl.getId(), rate));
                continue;
            }
            if (l.quantity() == null || l.quantity().signum() <= 0) throw bad(rl.getItemName() + ": quantity must be greater than zero");
            if (l.quantity().compareTo(rl.getQuantity()) > 0) throw bad(rl.getItemName() + ": cannot offer more than the " + plain(rl.getQuantity()) + " requested");
            if (l.unitPrice() == null || l.unitPrice().signum() < 0) throw bad(rl.getItemName() + ": unit price is required");
            BigDecimal disc = nz(l.discountPct());
            if (disc.signum() < 0 || disc.compareTo(BigDecimal.valueOf(100)) > 0) throw bad(rl.getItemName() + ": discount must be between 0 and 100");
            ScQuotationLine ql = ScQuotationLine.priced(tid, quotationId, rl.getId(), l.quantity(), l.unitPrice(), disc, rate);
            lines.add(ql);
            subtotal = subtotal.add(ql.getLineTotal());
            vat = vat.add(ql.vatAmount());
            anyPriced = true;
        }
        if (!anyPriced) throw bad("Price at least one line");
        // Delivery / other charges carry VAT at the standard rate.
        BigDecimal extras = delivery.add(other);
        vat = vat.add(extras.multiply(vatRateProvider.ratePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal totalIncl = subtotal.add(extras).add(vat);

        ScQuotation q;
        String eventType;
        String detail;
        if (existing == null) {
            q = ScQuotation.createWithId(quotationId, tid, rfqId, req.supplierId(), invitation.getSupplierName(), userId, userName);
            eventType = "QUOTE_RECEIVED";
            detail = invitation.getSupplierName();
        } else {
            q = existing;
            eventType = "QUOTE_REVISED";
            detail = invitation.getSupplierName() + ": landed R " + q.getLandedCost() + " -> ";
            quoteLineRepo.deleteByQuotation(tid, quotationId);
        }
        q.setOffer(trim(req.quoteReference()), req.validUntil(), req.leadTimeDays(), req.warrantyMonths(),
                req.paymentTermsDays(), delivery, other, subtotal, totalIncl, trim(req.notes()), userId, userName);
        quoteRepo.save(q);
        quoteLineRepo.saveAll(lines);
        invitation.markResponded();
        inviteRepo.save(invitation);
        eventRepo.save(ScRfqEvent.of(tid, rfqId, eventType,
                detail + (existing == null ? ": landed R " : "R ") + q.getLandedCost() + ", lead " + q.getLeadTimeDays() + " days", userId, userName));
        return toResponse(q);
    }

    // ── Evaluation / status ───────────────────────────────────────────────────

    @Transactional
    public QuotationResponse evaluate(TenantId tenantId, UUID rfqId, UUID quotationId, Integer score, String notes,
                                      UUID userId, String userName) {
        if (score == null) throw bad("technicalScore is required (0-100)");
        ScQuotation q = loadForChange(tenantId, rfqId, quotationId);
        q.evaluate(score, notes, userId, userName);
        quoteRepo.save(q);
        eventRepo.save(ScRfqEvent.of(tenantId.getValue(), rfqId, "QUOTE_SCORED", q.getSupplierName() + ": technical " + score, userId, userName));
        return toResponse(q);
    }

    @Transactional
    public QuotationResponse disqualify(TenantId tenantId, UUID rfqId, UUID quotationId, String reason, UUID userId, String userName) {
        requireReason(reason);
        ScQuotation q = loadForChange(tenantId, rfqId, quotationId);
        q.disqualify(reason.trim());
        quoteRepo.save(q);
        eventRepo.save(ScRfqEvent.of(tenantId.getValue(), rfqId, "QUOTE_DISQUALIFIED", q.getSupplierName() + ": " + reason.trim(), userId, userName));
        return toResponse(q);
    }

    @Transactional
    public QuotationResponse withdraw(TenantId tenantId, UUID rfqId, UUID quotationId, String reason, UUID userId, String userName) {
        requireReason(reason);
        ScQuotation q = loadForChange(tenantId, rfqId, quotationId);
        q.withdraw(reason.trim());
        quoteRepo.save(q);
        eventRepo.save(ScRfqEvent.of(tenantId.getValue(), rfqId, "QUOTE_WITHDRAWN", q.getSupplierName() + ": " + reason.trim(), userId, userName));
        return toResponse(q);
    }

    // ── Quote document ────────────────────────────────────────────────────────

    @Transactional
    public QuotationResponse attachFile(TenantId tenantId, UUID rfqId, UUID quotationId, MultipartFile file, UUID userId, String userName) {
        if (file == null || file.isEmpty()) throw bad("A file is required");
        ScQuotation q = loadForChange(tenantId, rfqId, quotationId);
        EvidenceResponse ev = evidenceFacade.attach(tenantId, file, "SUPPLIER_QUOTATION", "supplychain", "ScQuotation",
                q.getId(), null, userId, userName);
        q.attachFile(ev.id(), ev.fileName(), ev.contentType());
        quoteRepo.save(q);
        eventRepo.save(ScRfqEvent.of(tenantId.getValue(), rfqId, "QUOTE_FILE_ATTACHED", q.getSupplierName() + ": " + ev.fileName(), userId, userName));
        return toResponse(q);
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID rfqId, UUID quotationId) {
        ScRfq rfq = rfqRepo.findByTenantAndId(tenantId.getValue(), rfqId).orElseThrow(this::rfqNotFound);
        comparison.requireUnsealed(rfq);
        ScQuotation q = quote(tenantId.getValue(), rfqId, quotationId);
        if (q.getEvidenceId() == null) throw new HandyFlowException("No document attached", HttpStatus.NOT_FOUND, "NO_FILE");
        return evidenceFacade.download(tenantId, q.getEvidenceId());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuotationResponse> list(TenantId tenantId, UUID rfqId) {
        ScRfq rfq = rfqRepo.findByTenantAndId(tenantId.getValue(), rfqId).orElseThrow(this::rfqNotFound);
        comparison.requireUnsealed(rfq);
        return quoteRepo.findByRfq(tenantId.getValue(), rfqId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(TenantId tenantId, UUID rfqId, UUID quotationId) {
        ScRfq rfq = rfqRepo.findByTenantAndId(tenantId.getValue(), rfqId).orElseThrow(this::rfqNotFound);
        comparison.requireUnsealed(rfq);
        return toResponse(quote(tenantId.getValue(), rfqId, quotationId));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** Scoring / disqualifying / withdrawing / attaching is only meaningful while the RFQ is live (not draft, awarded or cancelled). */
    private ScQuotation loadForChange(TenantId tenantId, UUID rfqId, UUID quotationId) {
        ScRfq rfq = rfqRepo.lockByTenantAndId(tenantId.getValue(), rfqId).orElseThrow(this::rfqNotFound);
        if (rfq.getStatus() != RfqStatus.OPEN && rfq.getStatus() != RfqStatus.CLOSED)
            throw conflict("Quotations can only be changed while the RFQ is OPEN or CLOSED — this one is " + rfq.getStatus());
        return quote(tenantId.getValue(), rfqId, quotationId);
    }

    private ScQuotation quote(UUID tid, UUID rfqId, UUID quotationId) {
        ScQuotation q = quoteRepo.findByTenantAndId(tid, quotationId)
                .orElseThrow(() -> new HandyFlowException("Quotation not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!q.getRfqId().equals(rfqId)) throw bad("Quotation does not belong to this RFQ");
        return q;
    }

    QuotationResponse toResponse(ScQuotation q) {
        List<QuotationResponse.Line> lines = quoteLineRepo.findByQuotation(q.getTenantId(), q.getId()).stream()
                .map(l -> new QuotationResponse.Line(l.getId(), l.getRfqLineId(), l.isNoBid(), l.getQtyOffered(), l.getUnitPrice(),
                        l.getDiscountPct(), l.isNoBid() ? null : l.netUnitPrice(), l.getVatRate(), l.getLineTotal())).toList();
        return new QuotationResponse(q.getId(), q.getRfqId(), q.getSupplierId(), q.getSupplierName(), q.getStatus().name(),
                q.getStatusReason(), q.getQuoteReference(), q.getReceivedAt(), q.getValidUntil(), q.getLeadTimeDays(),
                q.getWarrantyMonths(), q.getPaymentTermsDays(), q.getDeliveryCost(), q.getOtherCharges(), q.getSubtotal(),
                q.getLandedCost(), q.getTotalInclVat(), q.getTechnicalScore(), q.getTechnicalNotes(), q.getEvaluatedByName(),
                q.getNotes(), q.getEvidenceId() != null, q.getFileName(), q.getCapturedByName(), lines);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new HandyFlowException("A reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
    }

    private HandyFlowException rfqNotFound() { return new HandyFlowException("RFQ not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
    private HandyFlowException conflict(String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, "INVALID_RFQ_STATE"); }
    private HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "INVALID_QUOTATION"); }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
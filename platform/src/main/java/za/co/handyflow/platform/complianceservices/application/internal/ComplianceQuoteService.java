package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.CalculateRequest;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.CalculateResponse;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.DisbursementInput;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.ExtraInput;
import za.co.handyflow.platform.complianceservices.dto.QuoteDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Quotes for clients. The server works out every figure from the price book version pinned when the quote was
 * started, so a quote shows what the client was given even if prices change later. Only services with an approved
 * version in force can be quoted. A discount above the consultant limit needs an administrator's approval.
 */
@Service
@RequiredArgsConstructor
public class ComplianceQuoteService {

    private final ComplianceClientRepository clients;
    private final ComplianceQuoteRepository quotes;
    private final ComplianceQuoteLineRepository lines;
    private final ComplianceQuoteAdjustmentRepository adjustments;
    private final ComplianceApprovalRuleRepository approvalRules;
    private final ComplianceServiceDefinitionRepository services;
    private final ComplianceServiceVersionRepository serviceVersions;
    private final PricingCalculatorService calculator;
    private final TenantSequenceService sequences;

    @Transactional(readOnly = true)
    public List<QuoteSummary> listForClient(TenantId tenantId, UUID clientId, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        List<QuoteSummary> out = new ArrayList<>();
        for (ComplianceQuote q : quotes.findForClient(tenantId, clientId)) out.add(summary(q, c.getName(), today));
        return out;
    }

    /** Quotes across all clients, optionally only those with one of the given statuses (EXPIRED is worked out, not stored). */
    @Transactional(readOnly = true)
    public List<QuoteSummary> listAll(TenantId tenantId, String status, LocalDate today) {
        List<ComplianceQuote> found = quotes.findAllForTenant(tenantId);
        Map<UUID, String> names = new HashMap<>();
        List<QuoteSummary> out = new ArrayList<>();
        for (ComplianceQuote q : found) {
            if (status != null && !status.isBlank() && !q.effectiveStatus(today).equals(status.trim().toUpperCase())) continue;
            String name = names.computeIfAbsent(q.getClientId(), id -> clients.findByIdForTenant(tenantId, id).map(ComplianceClient::getName).orElse(null));
            out.add(summary(q, name, today));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public QuoteView get(TenantId tenantId, UUID clientId, UUID id, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        return view(tenantId, find(tenantId, clientId, id), c.getName(), today);
    }

    @Transactional
    public QuoteView create(TenantId tenantId, UUID clientId, SaveRequest r, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        CompliancePriceBookVersion book = calculator.bookInForce(tenantId, today);
        String number = String.format("CQ-%06d", sequences.nextValue(tenantId, "CS_QUOTE"));
        ComplianceQuote q = ComplianceQuote.draft(tenantId, clientId, number, book.getVersionNumber(), book.getCurrency(), by);
        guard(() -> { q.edit(r.title(), r.validUntil() == null ? QuoteRules.defaultValidUntil(today) : r.validUntil(), r.notes(), r.terms(), today); return null; });
        quotes.save(q);
        writeLines(tenantId, q, book, r.lines() == null ? List.of() : r.lines(), today);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView save(TenantId tenantId, UUID clientId, UUID id, SaveRequest r, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.edit(r.title(), r.validUntil(), r.notes(), r.terms(), today); return null; });
        CompliancePriceBookVersion book = calculator.bookNumbered(tenantId, q.getPriceBookVersion());
        if (r.lines() != null) {
            lines.deleteForQuote(q.getId());
            writeLines(tenantId, q, book, r.lines(), today);
        }
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView submit(TenantId tenantId, UUID clientId, UUID id, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        int count = lines.findForQuotes(tenantId, List.of(q.getId())).size();
        guard(() -> { q.submit(by, count, today); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView approve(TenantId tenantId, UUID clientId, UUID id, ApproveRequest r, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.approve(by, r == null ? null : r.note(), r == null ? null : r.exceptionReason()); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView reject(TenantId tenantId, UUID clientId, UUID id, String reason, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.reject(by, reason); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView markSent(TenantId tenantId, UUID clientId, UUID id, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.markSent(by, today); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView accept(TenantId tenantId, UUID clientId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.accept(by, note, today); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView decline(TenantId tenantId, UUID clientId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.decline(by, note, today); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    @Transactional
    public QuoteView withdraw(TenantId tenantId, UUID clientId, UUID id, String reason, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = find(tenantId, clientId, id);
        guard(() -> { q.withdraw(reason); return null; });
        quotes.save(q);
        return view(tenantId, q, c.getName(), today);
    }

    // ---- pricing ----

    /** Prices every line on the pinned book, stores them, and takes the totals and the approver the discounts call for. */
    private void writeLines(TenantId tenantId, ComplianceQuote q, CompliancePriceBookVersion book, List<LineInput> inputs, LocalDate today) {
        Set<String> seen = new HashSet<>();
        List<QuoteRules.Priced> priced = new ArrayList<>();
        List<BigDecimal> discounts = new ArrayList<>();
        int order = 0;
        for (LineInput in : inputs) {
            final String code = codeOf(in.serviceCode());
            final int position = order;
            if (!seen.add(code)) throw new HandyFlowException(code + " is on this quote twice. Change its quantity or add an extra instead", HttpStatus.BAD_REQUEST, "QUOTE_INVALID");
            ComplianceServiceVersion sold = versionOnSale(tenantId, code, today);
            CalculateResponse res = calculator.priceOn(tenantId, book, new CalculateRequest(code, null, in.units(), in.hours(), in.complexityPct(), in.urgent(),
                    in.extras(), in.disbursements(), in.discountPct()));
            ComplianceQuoteLine line = guard(() -> ComplianceQuoteLine.of(tenantId, q.getId(), new ComplianceQuoteLine.Input(code, sold.getName(), sold.getVersionNumber(),
                    res.pricingModel(), in.description(), in.units(), in.hours(), in.complexityPct(), in.urgent(), in.discountPct()), position));
            List<PricingRules.Part> parts = res.parts().stream().map(p -> new PricingRules.Part(p.label(), p.amount())).toList();
            line.priced(res.professionalBeforeDiscount(), res.discount(), res.professional(), res.disbursements(), res.vat(), res.total(), QuoteRules.breakdownText(parts));
            lines.save(line);
            int slot = 0;
            if (in.extras() != null) {
                for (ExtraInput e : in.extras()) {
                    final int s = slot++;
                    adjustments.save(guard(() -> ComplianceQuoteAdjustment.extra(tenantId, line.getId(), e.description(), e.quantity(), e.unitPrice(), s)));
                }
            }
            if (in.disbursements() != null) {
                for (DisbursementInput d : in.disbursements()) {
                    final int s = slot++;
                    adjustments.save(guard(() -> ComplianceQuoteAdjustment.disbursement(tenantId, line.getId(), d.description(), d.amount(), d.vatable(), s)));
                }
            }
            priced.add(new QuoteRules.Priced(res.professional(), res.discount(), res.disbursements(), res.vat(), res.total(), res.vatRatePct()));
            discounts.add(line.getDiscountPct());
            order++;
        }
        BigDecimal max = discounts.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        QuoteRules.Totals totals = QuoteRules.sum(priced);
        String clientRisk = clients.findByIdForTenant(tenantId, q.getClientId()).map(ComplianceClient::getRiskRating).orElse(null);
        List<ApprovalRules.Rule> active = approvalRules.findActive(tenantId).stream().map(ComplianceApprovalRule::asRule).toList();
        ApprovalRules.Outcome outcome = ApprovalRules.apply(QuoteRules.requiredApprover(discounts), active, totals.professional(), seen, clientRisk, q.getClientId());
        guard(() -> { q.applyPricing(totals, outcome.requiredApprover(), max, outcome.basis()); return null; });
    }

    /** A service can be quoted only while an approved version of it is in force. */
    private ComplianceServiceVersion versionOnSale(TenantId tenantId, String code, LocalDate today) {
        ComplianceServiceDefinition def = services.findByCode(tenantId, code)
                .orElseThrow(() -> new HandyFlowException("Service " + code + " is not in the service catalogue", HttpStatus.BAD_REQUEST, "QUOTE_SERVICE_UNKNOWN"));
        List<ComplianceServiceVersion> vs = serviceVersions.findForService(tenantId, def.getId());
        Integer n = ObligationRules.effectiveOn(vs.stream().map(ComplianceServiceVersion::span).toList(), today);
        if (n == null) throw new HandyFlowException("Service " + code + " is not on sale: it has no approved version in force today", HttpStatus.CONFLICT, "QUOTE_SERVICE_NOT_ON_SALE");
        return vs.stream().filter(v -> v.getVersionNumber() == n).findFirst()
                .orElseThrow(() -> new HandyFlowException("Service " + code + " is not on sale", HttpStatus.CONFLICT, "QUOTE_SERVICE_NOT_ON_SALE"));
    }

    // ---- views ----

    private QuoteSummary summary(ComplianceQuote q, String clientName, LocalDate today) {
        return new QuoteSummary(q.getId(), q.getClientId(), clientName, q.getQuoteNumber(), q.getTitle(), q.effectiveStatus(today), q.getValidUntil(), q.getTotal(),
                q.getRequiredApprover(), q.getMaxDiscountPct(), q.getCreatedAt());
    }

    private QuoteView view(TenantId tenantId, ComplianceQuote q, String clientName, LocalDate today) {
        List<ComplianceQuoteLine> ls = lines.findForQuotes(tenantId, List.of(q.getId()));
        List<UUID> lineIds = ls.stream().map(ComplianceQuoteLine::getId).toList();
        Map<UUID, List<ComplianceQuoteAdjustment>> adj = lineIds.isEmpty() ? Map.of() : adjustments.findForLines(tenantId, lineIds).stream()
                .collect(Collectors.groupingBy(ComplianceQuoteAdjustment::getLineId));
        List<LineView> lineViews = new ArrayList<>();
        for (ComplianceQuoteLine l : ls) {
            List<ComplianceQuoteAdjustment> mine = adj.getOrDefault(l.getId(), List.of());
            List<ExtraView> extras = new ArrayList<>();
            List<DisbursementView> disb = new ArrayList<>();
            for (ComplianceQuoteAdjustment a : mine) {
                if (ComplianceQuoteAdjustment.EXTRA.equals(a.getKind())) extras.add(new ExtraView(a.getDescription(), a.getQuantity(), a.getAmount()));
                else disb.add(new DisbursementView(a.getDescription(), a.getAmount(), a.isVatable()));
            }
            List<PartView> parts = new ArrayList<>();
            for (PricingRules.Part p : QuoteRules.parseBreakdown(l.getBreakdown())) parts.add(new PartView(p.label(), p.amount()));
            lineViews.add(new LineView(l.getId(), l.getServiceCode(), l.getServiceName(), l.getServiceVersion(), l.getPricingModel(), l.getDescription(), l.getUnits(),
                    l.getHours(), l.getComplexityPct(), l.isUrgent(), l.getDiscountPct(), l.getBeforeDiscount(), l.getDiscount(), l.getProfessional(), l.getDisbursements(),
                    l.getVat(), l.getTotal(), parts, extras, disb));
        }
        return new QuoteView(q.getId(), q.getClientId(), clientName, q.getQuoteNumber(), q.getTitle(), q.effectiveStatus(today), q.getPriceBookVersion(), q.getCurrency(),
                q.getValidUntil(), q.getNotes(), q.getTerms(), q.getRequiredApprover(), q.getMaxDiscountPct(), q.getProfessional(), q.getDiscountTotal(), q.getDisbursements(),
                q.getVat(), q.getVatRatePct(), q.getTotal(), q.getCreatedBy(), q.getSubmittedBy(), q.getSubmittedAt(), q.getApprovedBy(), q.getApprovedAt(), q.getApprovalNote(),
                q.getApprovalExceptionReason(), q.getSentAt(), q.getDecidedBy(), q.getDecidedAt(), q.getDecisionNote(), q.getWithdrawnReason(), q.getCreatedAt(), lineViews, q.getApprovalBasis());
    }

    // ---- helpers ----

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceQuote find(TenantId tenantId, UUID clientId, UUID id) {
        ComplianceQuote q = quotes.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceQuote", id.toString()));
        if (!q.getClientId().equals(clientId)) throw new ResourceNotFoundException("ComplianceQuote", id.toString());
        return q;
    }

    private static String codeOf(String raw) {
        try {
            return ObligationRules.code(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "QUOTE_INVALID");
        }
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "QUOTE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "QUOTE_INVALID");
        }
    }
}

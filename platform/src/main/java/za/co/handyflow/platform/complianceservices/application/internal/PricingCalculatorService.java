package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Prices a service from the price book in force on a date. It works the number out; a person decides to quote it. */
@Service
@RequiredArgsConstructor
public class PricingCalculatorService {

    private final CompliancePriceBookVersionRepository versions;
    private final CompliancePriceBookLineRepository lines;
    private final CompliancePriceBookRateRepository rates;
    private final VatRateProvider vat;

    @Transactional(readOnly = true)
    public CalculateResponse calculate(TenantId tenantId, CalculateRequest r) {
        return priceOn(tenantId, bookInForce(tenantId, r.asOf() == null ? LocalDate.now() : r.asOf()), r);
    }

    /** Prices a request on one named price book version. A quote uses this so it stays on the version it was started on. */
    @Transactional(readOnly = true)
    public CalculateResponse priceOn(TenantId tenantId, CompliancePriceBookVersion book, CalculateRequest r) {
        String code = codeOf(r.serviceCode());
        CompliancePriceBookLine line = lines.findForVersions(tenantId, List.of(book.getId())).stream().filter(l -> l.getServiceCode().equals(code)).findFirst()
                .orElseThrow(() -> new HandyFlowException("Price book version " + book.getVersionNumber() + " has no price for " + code, HttpStatus.NOT_FOUND, "PRICE_NOT_FOUND"));
        BigDecimal hourly = null;
        if (line.getHourlyGrade() != null) {
            hourly = rates.findForVersions(tenantId, List.of(book.getId())).stream().filter(x -> x.getGrade().equals(line.getHourlyGrade())).findFirst()
                    .map(CompliancePriceBookRate::getHourlyRate)
                    .orElseThrow(() -> new HandyFlowException("Grade " + line.getHourlyGrade() + " has no hourly rate in this price book", HttpStatus.CONFLICT, "PRICE_RATE_MISSING"));
        }
        try {
            PricingRules.Line pl = new PricingRules.Line(line.getPricingModel(), line.getBaseFee(), line.getUnitRate(), hourly, line.getMinFee(),
                    line.getUrgentSurchargePct(), line.getTaxTreatment());
            List<PricingRules.Extra> extras = r.extras() == null ? List.of() : r.extras().stream().map(e -> new PricingRules.Extra(e.description(), e.quantity(), e.unitPrice())).toList();
            List<PricingRules.Disbursement> disb = r.disbursements() == null ? List.of()
                    : r.disbursements().stream().map(d -> new PricingRules.Disbursement(d.description(), d.amount(), d.vatable())).toList();
            PricingRules.Result res = PricingRules.price(pl, new PricingRules.Inputs(r.units(), r.hours(), r.complexityPct(), r.urgent(), extras, disb, r.discountPct()),
                    vat.ratePercent());
            return new CalculateResponse(code, line.getPricingModel(), book.getVersionNumber(), book.getCurrency(), res.parts().stream().map(p -> new PartView(p.label(), p.amount())).toList(),
                    res.professionalBeforeDiscount(), res.discount(), res.professional(), res.disbursements(), res.vat(), res.vatRatePct(), res.total(), res.discountApprover());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PRICE_INVALID");
        }
    }

    /** The price book version with this number, whatever its dates. */
    @Transactional(readOnly = true)
    public CompliancePriceBookVersion bookNumbered(TenantId tenantId, int number) {
        return versions.findAllForTenant(tenantId).stream().filter(v -> v.isApproved() && v.getVersionNumber() == number).findFirst()
                .orElseThrow(() -> new HandyFlowException("Price book version " + number + " was not found", HttpStatus.CONFLICT, "PRICE_BOOK_NOT_FOUND"));
    }

    /** What a job costs the practice and what to charge for a target margin. Administrators only: it uses cost rates. */
    @Transactional(readOnly = true)
    public MarginResponse margin(TenantId tenantId, MarginRequest r) {
        CompliancePriceBookVersion book = bookInForce(tenantId, r.asOf() == null ? LocalDate.now() : r.asOf());
        final String grade = gradeOf(r.grade());
        BigDecimal costRate = rates.findForVersions(tenantId, List.of(book.getId())).stream().filter(x -> x.getGrade().equals(grade)).findFirst()
                .map(CompliancePriceBookRate::getCostRate)
                .orElseThrow(() -> new HandyFlowException("Grade " + grade + " has no cost rate in price book version " + book.getVersionNumber(), HttpStatus.CONFLICT, "PRICE_COST_MISSING"));
        try {
            BigDecimal cost = PricingRules.cost(r.hours(), costRate, r.externalCosts(), r.overheadPct());
            BigDecimal price = PricingRules.priceForMargin(cost, r.targetMarginPct() == null ? BigDecimal.ZERO : r.targetMarginPct());
            BigDecimal rounded = PricingRules.roundUpTo(price, r.roundStep());
            BigDecimal proposed = r.proposedPrice() == null ? null : PricingRules.money(r.proposedPrice(), "Proposed price");
            return new MarginResponse(book.getVersionNumber(), cost, price, rounded, PricingRules.grossMargin(rounded, cost),
                    proposed == null ? null : PricingRules.grossMargin(proposed, cost));
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PRICE_INVALID");
        }
    }

    /** The approved price book version in force on a date. */
    @Transactional(readOnly = true)
    public CompliancePriceBookVersion bookInForce(TenantId tenantId, LocalDate on) {
        List<CompliancePriceBookVersion> all = versions.findAllForTenant(tenantId);
        Integer n = ObligationRules.effectiveOn(all.stream().map(CompliancePriceBookVersion::span).toList(), on);
        if (n == null) throw new HandyFlowException("No approved price book is in force on " + on, HttpStatus.CONFLICT, "PRICE_BOOK_NOT_IN_FORCE");
        return all.stream().filter(v -> v.getVersionNumber() == n).findFirst()
                .orElseThrow(() -> new HandyFlowException("No approved price book is in force on " + on, HttpStatus.CONFLICT, "PRICE_BOOK_NOT_IN_FORCE"));
    }

    private static String gradeOf(String raw) {
        try {
            return PricingRules.grade(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PRICE_INVALID");
        }
    }

    private static String codeOf(String raw) {
        try {
            return ObligationRules.code(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PRICE_INVALID");
        }
    }
}

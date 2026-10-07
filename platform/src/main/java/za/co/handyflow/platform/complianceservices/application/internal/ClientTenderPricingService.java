package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.tenderpricing.TenderPriceCalculator.Breakdown;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPricing;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPricingLine;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPricingLineRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPricingRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.dto.SaveClientTenderPricingLineRequest;
import za.co.handyflow.platform.complianceservices.dto.SaveClientTenderPricingSettingsRequest;
import za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.tenderpricing.TenderPriceCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client tender pricing (ADR-004, client side). Pricing is editable while the tender is still being prepared (DRAFT to READY_TO_SUBMIT) and locked from SUBMITTED onwards, including
 * WITHDRAWN: after submission the schedule is the record of what was priced, and the same figures are frozen into the submission snapshot.
 * Every write goes through {@link #editableTender}, so no route can edit a locked schedule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTenderPricingService {

    /** Statuses in which the price can still change. READY_TO_SUBMIT is included because the lifecycle has no way back from it. */
    static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT");

    private final ClientTenderRepository tenderRepository;
    private final ClientTenderPricingRepository pricingRepository;
    private final ClientTenderPricingLineRepository lineRepository;
    private final VatRateProvider vatRateProvider;

    @Transactional(readOnly = true)
    public ClientTenderPricingResponse getPricing(TenantId tenantId, UUID tenderId) {
        ClientTender tender = tender(tenantId, tenderId);
        return build(tenantId, tender, pricingRepository.findByTender(tenantId, tenderId).orElse(null));
    }

    /** The priced schedule for the submission snapshot, or null when the tender was never priced (no settings saved and no lines). */
    @Transactional(readOnly = true)
    public ClientTenderPricingResponse snapshotOf(TenantId tenantId, UUID tenderId) {
        ClientTenderPricingResponse r = getPricing(tenantId, tenderId);
        return r.configured() || !r.lines().isEmpty() ? r : null;
    }

    @Transactional
    public ClientTenderPricingResponse saveSettings(TenantId tenantId, UUID tenderId, SaveClientTenderPricingSettingsRequest req, UUID by) {
        ClientTender tender = editableTender(tenantId, tenderId);
        ClientTenderPricing pricing = ensurePricing(tenantId, tenderId, by);
        pricing.update(req.overheadPct(), req.contingencyPct(), req.profitPct(),
                req.vatApplies() == null || req.vatApplies(), req.notes(), by);
        pricingRepository.save(pricing);
        log.info("Client tender pricing settings saved tender={} tenant={}", tenderId, tenantId);
        return build(tenantId, tender, pricing);
    }

    @Transactional
    public ClientTenderPricingResponse addLine(TenantId tenantId, UUID tenderId, SaveClientTenderPricingLineRequest req, UUID by) {
        ClientTender tender = editableTender(tenantId, tenderId);
        ClientTenderPricing pricing = ensurePricing(tenantId, tenderId, by);
        int next = lineRepository.maxSortOrder(tenantId, tenderId) + 1;
        lineRepository.save(ClientTenderPricingLine.create(tenantId, tenderId, req.section(), req.itemRef(), req.description(),
                req.unit(), req.quantity(), req.unitCost(), next, by));
        log.info("Client tender pricing line added tender={} tenant={}", tenderId, tenantId);
        return build(tenantId, tender, pricing);
    }

    @Transactional
    public ClientTenderPricingResponse updateLine(TenantId tenantId, UUID lineId, SaveClientTenderPricingLineRequest req, UUID by) {
        ClientTenderPricingLine line = line(tenantId, lineId);
        ClientTender tender = editableTender(tenantId, line.getTenderId());
        line.update(req.section(), req.itemRef(), req.description(), req.unit(), req.quantity(), req.unitCost(), by);
        lineRepository.save(line);
        return build(tenantId, tender, pricingRepository.findByTender(tenantId, line.getTenderId()).orElse(null));
    }

    @Transactional
    public ClientTenderPricingResponse deleteLine(TenantId tenantId, UUID lineId) {
        ClientTenderPricingLine line = line(tenantId, lineId);
        ClientTender tender = editableTender(tenantId, line.getTenderId());
        lineRepository.delete(line);
        log.info("Client tender pricing line removed id={} tenant={}", lineId, tenantId);
        return build(tenantId, tender, pricingRepository.findByTender(tenantId, line.getTenderId()).orElse(null));
    }

    private ClientTender tender(TenantId tenantId, UUID tenderId) {
        return tenderRepository.findByIdForTenant(tenantId, tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", tenderId.toString()));
    }

    private ClientTenderPricingLine line(TenantId tenantId, UUID lineId) {
        return lineRepository.findByIdForTenant(tenantId, lineId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTenderPricingLine", lineId.toString()));
    }

    private ClientTender editableTender(TenantId tenantId, UUID tenderId) {
        ClientTender tender = tender(tenantId, tenderId);
        if (!EDITABLE_STATUSES.contains(tender.getStatus())) {
            throw new IllegalStateException("Pricing is locked because this tender is " + tender.getStatus()
                    + ". It can only be changed up to the point of submission.");
        }
        return tender;
    }

    private ClientTenderPricing ensurePricing(TenantId tenantId, UUID tenderId, UUID by) {
        return pricingRepository.findByTender(tenantId, tenderId).orElseGet(() -> {
            try {
                return pricingRepository.saveAndFlush(ClientTenderPricing.create(tenantId, tenderId, vatRateProvider.ratePercent(), by));
            } catch (DataIntegrityViolationException e) {
                // Someone else created this tender's pricing at the same moment (unique index on tender_id); ask the caller to retry rather than fail with a 500.
                throw new IllegalStateException("This tender's pricing was just created by someone else. Reload it and try again.", e);
            }
        });
    }

    private ClientTenderPricingResponse build(TenantId tenantId, ClientTender tender, ClientTenderPricing pricing) {
        List<ClientTenderPricingLine> lines = lineRepository.findByTender(tenantId, tender.getId());
        boolean configured = pricing != null;
        ClientTenderPricingResponse.Settings settings = configured
                ? new ClientTenderPricingResponse.Settings(pricing.getOverheadPct(), pricing.getContingencyPct(), pricing.getProfitPct(),
                        pricing.isVatApplies(), pricing.getVatRatePct(), pricing.getNotes())
                : new ClientTenderPricingResponse.Settings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        true, vatRateProvider.ratePercent(), null);

        Breakdown b = TenderPriceCalculator.calculate(
                lines.stream().map(l -> new TenderPriceCalculator.Line(l.getSection(), l.getQuantity(), l.getUnitCost())).toList(),
                new TenderPriceCalculator.Settings(settings.overheadPct(), settings.contingencyPct(), settings.profitPct(),
                        settings.vatApplies(), settings.vatRatePct()));

        return new ClientTenderPricingResponse(tender.getId(), tender.getStatus(), EDITABLE_STATUSES.contains(tender.getStatus()),
                configured, tender.getEstimatedValue(), settings,
                lines.stream().map(l -> new ClientTenderPricingResponse.LineResponse(l.getId(), l.getSection(), l.getItemRef(),
                        l.getDescription(), l.getUnit(), l.getQuantity(), l.getUnitCost(),
                        TenderPriceCalculator.lineTotal(l.getQuantity(), l.getUnitCost()), l.getSortOrder())).toList(),
                new ClientTenderPricingResponse.Breakdown(b.directCost(), b.overhead(), b.contingency(), b.profit(), b.priceExVat(),
                        b.vat(), b.priceInclVat(), b.marginPct(),
                        b.sections().stream().map(s -> new ClientTenderPricingResponse.SectionTotal(s.section(), s.lineCount(), s.subtotal())).toList()));
    }
}

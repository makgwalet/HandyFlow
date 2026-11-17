package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPriceRepository;
import za.co.handyflow.platform.fuel.dto.PriceRequest;
import za.co.handyflow.platform.fuel.dto.PriceResponse;
import za.co.handyflow.platform.fuel.dto.ResolvedPriceResponse;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Stage 4A: standard price list, customer contract prices and volume breaks. */
@Service
@RequiredArgsConstructor
public class FuelPricingService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelPriceRepository prices;
    private final FuelCustomerAccountRepository accounts;
    private final CrmFacade crm;
    private final FuelSettingsRepository settings;
    private final FuelReceiptRepository receipts;
    private final ApprovalFacade approvalFacade;
    private final FuelContractTierPricing contractTiers;

    @Transactional(readOnly = true)
    public List<PriceResponse> list(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        Map<UUID, String> names = new HashMap<>();
        List<PriceResponse> out = new ArrayList<>();
        for (FuelPrice p : prices.findAllForTenant(tenantId)) {
            String name = p.getAccountId() == null ? null : names.computeIfAbsent(p.getAccountId(), id -> accounts.findActiveById(tenantId, id)
                    .flatMap(a -> crm.findCustomerById(tenantId, a.getCrmCustomerId())).map(CustomerSummary::name).orElse("(customer removed)"));
            out.add(new PriceResponse(p.getId(), p.getFuelType(), p.getAccountId(), name, p.getPricePerLitre(), p.getMinLitres(),
                    p.getValidFrom(), p.getValidTo(), p.getNote(), p.isLive(today),
                    p.getApprovalStatus(), p.getApprovalReason(), p.getPreviousPricePerLitre(), p.changePct(), p.getCostPerLitreAtEntry(), p.marginPct()));
        }
        return out;
    }

    @Transactional
    public PriceResponse create(TenantId tenantId, PriceRequest req, UUID by) {
        if (req.accountId() != null) {
            accounts.findActiveById(tenantId, req.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer account", req.accountId().toString()));
        }
        FuelPrice p = FuelPrice.create(tenantId, req.fuelType(), req.accountId(), req.pricePerLitre(), req.minLitres(),
                req.validFrom(), req.validTo(), req.note(), by);
        List<FuelPrice> clash = req.accountId() == null
                ? prices.overlapStandard(tenantId, p.getFuelType(), p.getMinLitres(), p.getValidFrom(), p.getValidTo())
                : prices.overlapAccount(tenantId, p.getFuelType(), req.accountId(), p.getMinLitres(), p.getValidFrom(), p.getValidTo());
        if (!clash.isEmpty()) {
            throw new ConflictException("A price for " + p.getFuelType() + (p.getMinLitres().signum() > 0 ? " from " + p.getMinLitres().stripTrailingZeros().toPlainString() + " L" : "")
                    + " already covers those dates. End it first, then add the new price.");
        }
        BigDecimal previous = (req.accountId() == null
                ? prices.liveComparableStandard(tenantId, p.getFuelType(), p.getMinLitres(), p.getValidFrom())
                : prices.liveComparableAccount(tenantId, p.getFuelType(), req.accountId(), p.getMinLitres(), p.getValidFrom()))
                .stream().findFirst().map(FuelPrice::getPricePerLitre).orElse(null);
        p.recordContext(previous, latestCost(tenantId, p.getFuelType()));
        prices.save(p);
        String reason = approvalReason(settings.findByTenant(tenantId).orElse(null), p);
        if (reason != null) {
            // The Approvals engine decides. No rule for fuel prices means approved straight away; a failure is not swallowed,
            // because applying a price that should have waited is worse than asking the user to try again.
            Map<String, Object> meta = new HashMap<>();
            meta.put("fuelType", p.getFuelType());
            meta.put("pricePerLitre", p.getPricePerLitre().doubleValue());
            if (previous != null) meta.put("previousPricePerLitre", previous.doubleValue());
            if (p.changePct() != null) meta.put("changePct", p.changePct().doubleValue());
            if (p.marginPct() != null) meta.put("marginPct", p.marginPct().doubleValue());
            ApprovalRequestResponse r = approvalFacade.submit(tenantId, "fuel", "PRICE", p.getId(), by, meta);
            if (!"APPROVED".equals(r.status())) p.submitForApproval(r.id(), reason);
            prices.save(p);
        }
        return list(tenantId).stream().filter(x -> x.id().equals(p.getId())).findFirst().orElseThrow();
    }

    /** Why this price needs a second person, or null if it does not. Both checks are off until the tenant sets a limit. */
    static String approvalReason(FuelSettings s, FuelPrice p) {
        if (s == null) return null;
        List<String> why = new ArrayList<>();
        BigDecimal change = p.changePct();
        if (s.getPriceChangeApprovalPct() != null && change != null && change.abs().compareTo(s.getPriceChangeApprovalPct()) > 0) {
            why.add("Moves " + change.stripTrailingZeros().toPlainString() + "% from the current price (limit " + s.getPriceChangeApprovalPct().stripTrailingZeros().toPlainString() + "%)");
        }
        BigDecimal margin = p.marginPct();
        if (s.getMinMarginPct() != null && margin != null && margin.compareTo(s.getMinMarginPct()) < 0) {
            why.add("Margin " + margin.stripTrailingZeros().toPlainString() + "% is under the minimum " + s.getMinMarginPct().stripTrailingZeros().toPlainString() + "% on the latest purchase cost");
        }
        return why.isEmpty() ? null : String.join("; ", why);
    }

    /** Latest purchase cost per litre for the fuel type, or null when nothing has been received yet. */
    private BigDecimal latestCost(TenantId tenantId, String fuelType) {
        List<FuelReceipt> latest = receipts.latestForFuelType(tenantId, fuelType, PageRequest.of(0, 1));
        return latest.isEmpty() ? null : latest.get(0).getPricePerLitre();
    }

    /** Applies an approval outcome from the Approvals engine. Does nothing unless the price is still waiting, so repeats and races are harmless. */
    @Transactional
    public void applyApprovalOutcome(TenantId tenantId, UUID priceId, String outcome) {
        FuelPrice p = prices.findOwnedForUpdate(tenantId, priceId).orElse(null);
        if (p == null) return;
        boolean changed = "APPROVED".equals(outcome) ? p.approvalGranted() : "REJECTED".equals(outcome) && p.approvalRejected();
        if (changed) prices.save(p);
    }

    @Transactional
    public void end(TenantId tenantId, UUID id, LocalDate lastDay) {
        FuelPrice p = prices.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Price", id.toString()));
        p.end(lastDay != null ? lastDay : LocalDate.now(SAST));
        prices.save(p);
    }

    /** The price for this customer, product, quantity and day, or empty if none is set. */
    @Transactional(readOnly = true)
    public Optional<ResolvedPriceResponse> resolve(TenantId tenantId, UUID accountId, String fuelType, BigDecimal litres, LocalDate on) {
        // A contract with a volume ladder chooses among its own approved prices by the volume delivered so far. Without one, nothing changes.
        if (accountId != null) {
            Optional<ResolvedPriceResponse> tier = contractTiers.resolve(tenantId, accountId, fuelType, on);
            if (tier != null && tier.isPresent()) return tier;
        }
        List<FuelPrice> live = accountId == null ? prices.liveStandard(tenantId, fuelType, on) : prices.liveForAccount(tenantId, fuelType, accountId, on);
        return pick(live, accountId, litres == null ? BigDecimal.ZERO : litres);
    }

    /**
     * A customer's own (contract) price beats the standard list; within a list the highest volume break the
     * quantity reaches wins; if two still tie, the most recently started one.
     */
    static Optional<ResolvedPriceResponse> pick(List<FuelPrice> live, UUID accountId, BigDecimal litres) {
        Comparator<FuelPrice> best = Comparator.comparing(FuelPrice::getMinLitres).thenComparing(FuelPrice::getValidFrom);
        for (boolean contract : new boolean[]{true, false}) {
            Optional<FuelPrice> hit = live.stream()
                    .filter(p -> contract ? (accountId != null && accountId.equals(p.getAccountId())) : p.getAccountId() == null)
                    .filter(p -> litres.compareTo(p.getMinLitres()) >= 0)
                    .max(best);
            if (hit.isPresent()) {
                FuelPrice p = hit.get();
                return Optional.of(new ResolvedPriceResponse(p.getPricePerLitre(), contract ? "CONTRACT" : "STANDARD", p.getId(), p.getMinLitres()));
            }
        }
        return Optional.empty();
    }
}

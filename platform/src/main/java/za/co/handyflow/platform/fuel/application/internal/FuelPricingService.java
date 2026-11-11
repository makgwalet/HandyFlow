package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
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

    @Transactional(readOnly = true)
    public List<PriceResponse> list(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        Map<UUID, String> names = new HashMap<>();
        List<PriceResponse> out = new ArrayList<>();
        for (FuelPrice p : prices.findAllForTenant(tenantId)) {
            String name = p.getAccountId() == null ? null : names.computeIfAbsent(p.getAccountId(), id -> accounts.findActiveById(tenantId, id)
                    .flatMap(a -> crm.findCustomerById(tenantId, a.getCrmCustomerId())).map(CustomerSummary::name).orElse("(customer removed)"));
            out.add(new PriceResponse(p.getId(), p.getFuelType(), p.getAccountId(), name, p.getPricePerLitre(), p.getMinLitres(),
                    p.getValidFrom(), p.getValidTo(), p.getNote(), p.isLive(today)));
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
        prices.save(p);
        return list(tenantId).stream().filter(x -> x.id().equals(p.getId())).findFirst().orElseThrow();
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

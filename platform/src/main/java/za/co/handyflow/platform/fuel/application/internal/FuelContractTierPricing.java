package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelContract;
import za.co.handyflow.platform.fuel.domain.model.FuelContractPrice;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.repository.FuelContractPriceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelContractRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPriceRepository;
import za.co.handyflow.platform.fuel.dto.ResolvedPriceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contract volume pricing. The rule, in one place:
 * <ul>
 *   <li>Prices stay in the one price list and are created and approved there. A contract only chooses among its own linked prices.</li>
 *   <li>It applies only when the customer has exactly one active contract for the fuel that has a volume ladder (a linked price starting above 0 litres).
 *       None, or more than one, means this does nothing and the ordinary price resolution runs, so existing companies are unchanged.</li>
 *   <li>The step used is the live, approved linked price with the highest threshold the contract's delivered volume has reached. A step waiting for
 *       approval, rejected or ended never applies, so the next lower step does; with none qualifying the ordinary resolution runs.</li>
 *   <li>The price is read when an order or quote is priced and then stays on that order. Later deliveries do not re-price orders already made.</li>
 *   <li>A step's own order-size minimum is ignored while the ladder is in use: contract volume decides.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelContractTierPricing {

    private final FuelContractRepository contracts;
    private final FuelContractPriceRepository links;
    private final FuelPriceRepository prices;
    private final FuelCustomerAccountRepository accounts;
    private final FuelContractService contractService;

    @Transactional(readOnly = true)
    public Optional<ResolvedPriceResponse> resolve(TenantId tenant, UUID accountId, String fuelType, LocalDate on) {
        if (accountId == null || fuelType == null) return Optional.empty();
        List<FuelContract> active = contracts.findAllActive(tenant).stream()
                .filter(c -> accountId.equals(c.getAccountId()) && "ACTIVE".equals(c.statusOn(on))
                        && (c.getFuelType() == null || c.getFuelType().equalsIgnoreCase(fuelType)))
                .toList();
        List<FuelContract> ladders = active.stream().filter(c -> FuelContractService.hasLadder(links.findByContract(tenant, c.getId()))).toList();
        if (ladders.isEmpty()) return Optional.empty();
        if (ladders.size() > 1) {
            log.warn("More than one active volume contract for account {} and {}; contract tiers are not applied", accountId, fuelType);
            return Optional.empty();
        }
        FuelContract c = ladders.get(0);
        FuelCustomerAccount account = accounts.findActiveById(tenant, accountId).orElse(null);
        if (account == null) return Optional.empty();
        List<FuelContractPrice> rows = links.findByContract(tenant, c.getId());
        Map<UUID, FuelPrice> byId = new HashMap<>();
        for (FuelContractPrice l : rows) prices.findOwned(tenant, l.getPriceId()).ifPresent(p -> byId.put(p.getId(), p));
        BigDecimal delivered = contractService.deliveredLitres(tenant, c, account, on);
        return FuelContractService.pickTier(rows, byId, fuelType, delivered, on)
                .map(p -> new ResolvedPriceResponse(p.getPricePerLitre(), "CONTRACT", p.getId(), p.getMinLitres()));
    }
}

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelContract;
import za.co.handyflow.platform.fuel.domain.model.FuelContractPrice;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ContractDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 7c (B06b): supply contracts that group and report. A contract ties a customer account to dates, an optional volume commitment
 * and terms, and refers to the account's existing price entries. It never holds, creates, changes or approves a price: the price list
 * (with its approvals) stays the only price master. It reports litres delivered against the commitment from the delivered loads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelContractService {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final FuelContractRepository contracts;
    private final FuelContractPriceRepository links;
    private final FuelCustomerAccountRepository accounts;
    private final FuelPriceRepository prices;
    private final FuelDeliveryRepository deliveries;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<ContractView> list(TenantId tenantId, LocalDate today) {
        return contracts.findAllActive(tenantId).stream().map(c -> view(tenantId, c, today)).toList();
    }

    @Transactional(readOnly = true)
    public ContractView get(TenantId tenantId, UUID id, LocalDate today) {
        return view(tenantId, find(tenantId, id), today);
    }

    @Transactional
    public ContractView create(TenantId tenantId, CreateRequest req, UUID by, LocalDate today) {
        accounts.findActiveById(tenantId, req.accountId()).orElseThrow(() -> new ResourceNotFoundException("Customer account", String.valueOf(req.accountId())));
        if (req.contractNumber() != null && contracts.numberTaken(tenantId, req.contractNumber().trim()))
            throw new IllegalArgumentException("Contract number " + req.contractNumber().trim() + " is already used.");
        FuelContract c = FuelContract.create(tenantId, req.accountId(), req.contractNumber(), req.title(), req.startDate(), req.endDate(),
                req.fuelType(), req.committedLitres(), req.terms(), by);
        contracts.save(c);
        log.info("Contract {} created for account {}", c.getContractNumber(), c.getAccountId());
        return view(tenantId, c, today);
    }

    @Transactional
    public ContractView update(TenantId tenantId, UUID id, UpdateRequest req, LocalDate today) {
        FuelContract c = find(tenantId, id);
        c.update(req.title(), req.startDate(), req.endDate(), req.fuelType(), req.committedLitres(), req.terms());
        contracts.save(c);
        return view(tenantId, c, today);
    }

    @Transactional
    public ContractView cancel(TenantId tenantId, UUID id, CancelRequest req, LocalDate today) {
        FuelContract c = find(tenantId, id);
        c.cancel(req.reason());
        contracts.save(c);
        return view(tenantId, c, today);
    }

    /** Links an existing price entry of the same customer account. The price itself is not touched. */
    @Transactional
    public ContractView linkPrice(TenantId tenantId, UUID id, UUID priceId, UUID by, LocalDate today) {
        return linkPrice(tenantId, id, priceId, null, by, today);
    }

    /**
     * Links an existing price of the contract's customer. fromLitres (default 0) is the contract volume delivered before an order from which this price
     * applies. Linking again changes the threshold. Two prices of one fuel cannot start at the same volume, because the ladder would not know which to use.
     */
    @Transactional
    public ContractView linkPrice(TenantId tenantId, UUID id, UUID priceId, BigDecimal fromLitres, UUID by, LocalDate today) {
        FuelContract c = find(tenantId, id);
        if (c.isCancelled()) throw new IllegalStateException("A cancelled contract cannot be changed.");
        FuelPrice p = prices.findOwned(tenantId, priceId).orElseThrow(() -> new ResourceNotFoundException("Price", priceId.toString()));
        if (p.getAccountId() == null || !p.getAccountId().equals(c.getAccountId()))
            throw new IllegalArgumentException("That price is not set for this contract's customer account.");
        if (c.getFuelType() != null && !c.getFuelType().equalsIgnoreCase(p.getFuelType()))
            throw new IllegalArgumentException("That price is for " + p.getFuelType() + ", and this contract is for " + c.getFuelType() + ".");
        BigDecimal from = fromLitres == null ? BigDecimal.ZERO : fromLitres;
        if (from.signum() < 0) throw new IllegalArgumentException("The volume cannot be negative.");
        BigDecimal scaled = from.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() > 0) {
            for (FuelContractPrice other : links.findByContract(tenantId, id)) {
                if (other.getPriceId().equals(priceId) || other.getFromLitres().compareTo(scaled) != 0) continue;
                FuelPrice op = prices.findOwned(tenantId, other.getPriceId()).orElse(null);
                if (op != null && op.getFuelType().equalsIgnoreCase(p.getFuelType()))
                    throw new IllegalArgumentException("Another " + p.getFuelType() + " price already starts at " + scaled.stripTrailingZeros().toPlainString() + " L. Give each step its own volume.");
            }
        }
        FuelContractPrice link = links.findLink(tenantId, id, priceId).orElseGet(() -> FuelContractPrice.of(tenantId, id, priceId, by));
        link.setFromLitres(scaled);
        links.save(link);
        return view(tenantId, c, today);
    }

    @Transactional
    public ContractView unlinkPrice(TenantId tenantId, UUID id, UUID priceId, LocalDate today) {
        FuelContract c = find(tenantId, id);
        if (c.isCancelled()) throw new IllegalStateException("A cancelled contract cannot be changed.");
        links.findLink(tenantId, id, priceId).ifPresent(links::delete);
        return view(tenantId, c, today);
    }

    private FuelContract find(TenantId tenantId, UUID id) {
        return contracts.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    ContractView view(TenantId tenantId, FuelContract c, LocalDate today) {
        FuelCustomerAccount account = accounts.findActiveById(tenantId, c.getAccountId()).orElse(null);
        String name = account == null ? null : crm.findCustomerById(tenantId, account.getCrmCustomerId()).map(CustomerSummary::name).orElse("(customer removed)");
        BigDecimal delivered = deliveredLitres(tenantId, c, account, today);
        BigDecimal pct = null, remaining = null;
        if (c.getCommittedLitres() != null) {
            pct = delivered.multiply(BigDecimal.valueOf(100)).divide(c.getCommittedLitres(), 1, RoundingMode.HALF_UP);
            remaining = c.getCommittedLitres().subtract(delivered).max(BigDecimal.ZERO);
        }
        String status = c.statusOn(today);
        Long daysLeft = "ACTIVE".equals(status) ? ChronoUnit.DAYS.between(today, c.getEndDate()) : null;
        List<LinkedPrice> linked = new ArrayList<>();
        List<FuelContractPrice> linkRows = links.findByContract(tenantId, c.getId());
        Map<UUID, FuelPrice> priceById = new HashMap<>();
        for (FuelContractPrice l : linkRows) prices.findOwned(tenantId, l.getPriceId()).ifPresent(p -> priceById.put(p.getId(), p));
        Set<UUID> inEffect = new HashSet<>();
        if (hasLadder(linkRows)) {
            for (String fuel : priceById.values().stream().map(p -> p.getFuelType().toLowerCase()).collect(Collectors.toSet())) {
                pickTier(linkRows, priceById, fuel, delivered, today).ifPresent(p -> inEffect.add(p.getId()));
            }
        }
        for (FuelContractPrice l : linkRows) {
            FuelPrice p = priceById.get(l.getPriceId());
            if (p != null) linked.add(new LinkedPrice(p.getId(), p.getFuelType(), p.getPricePerLitre(), p.getMinLitres(), p.getValidFrom(), p.getValidTo(),
                    p.getApprovalStatus(), p.isLive(today), l.getFromLitres(), inEffect.contains(p.getId())));
        }
        return new ContractView(c.getId(), c.getAccountId(), name, c.getContractNumber(), c.getTitle(), c.getStartDate(), c.getEndDate(), c.getFuelType(),
                c.getCommittedLitres(), c.getTerms(), status, c.getCancelledReason(), delivered, pct, remaining, daysLeft, linked.size(), linked, c.getCreatedAt());
    }

    /** A contract has a volume ladder when at least one linked price starts above 0 litres. Without one nothing changes. */
    static boolean hasLadder(List<FuelContractPrice> linkRows) {
        return linkRows.stream().anyMatch(l -> l.getFromLitres().signum() > 0);
    }

    /**
     * The price a volume selects: among the linked prices of this fuel that are live and approved today, the one with the highest threshold the delivered
     * volume has reached. A price waiting for approval, rejected or ended never applies, so the next lower step does. Empty when none qualifies.
     */
    static Optional<FuelPrice> pickTier(List<FuelContractPrice> linkRows, Map<UUID, FuelPrice> priceById, String fuelType, BigDecimal delivered, LocalDate today) {
        FuelPrice best = null; BigDecimal bestFrom = null;
        for (FuelContractPrice l : linkRows) {
            FuelPrice p = priceById.get(l.getPriceId());
            if (p == null || !p.getFuelType().equalsIgnoreCase(fuelType) || !p.isLive(today)) continue;
            if (delivered.compareTo(l.getFromLitres()) < 0) continue;
            if (bestFrom == null || l.getFromLitres().compareTo(bestFrom) > 0) { best = p; bestFrom = l.getFromLitres(); }
        }
        return Optional.ofNullable(best);
    }

    /** Delivered litres for the account from the start date to today or the end date, whichever is earlier. Nothing before the start counts. */
    BigDecimal deliveredLitres(TenantId tenantId, FuelContract c, FuelCustomerAccount account, LocalDate today) {
        if (account == null || today.isBefore(c.getStartDate())) return BigDecimal.ZERO;
        LocalDate last = today.isAfter(c.getEndDate()) ? c.getEndDate() : today;
        var from = c.getStartDate().atStartOfDay(ZONE).toInstant();
        var to = last.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);
        BigDecimal sum = BigDecimal.ZERO;
        for (FuelDelivery d : deliveries.findDeliveredBetween(tenantId, from, to)) {
            if (!account.getCrmCustomerId().equals(d.getCustomerId())) continue;
            if (c.getFuelType() != null && !c.getFuelType().equalsIgnoreCase(d.getFuelType())) continue;
            if (d.getLitresDelivered() != null) sum = sum.add(d.getLitresDelivered());
        }
        return sum;
    }
}

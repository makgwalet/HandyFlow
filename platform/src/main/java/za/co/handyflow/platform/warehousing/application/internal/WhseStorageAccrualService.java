package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocationAccrual;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.model.WhseStorageAccrual;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationAccrualRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseStorageAccrualRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes one accrual per stocked position per day. Safe to run twice for the same day, and safe for a missed past day
 * because the quantity comes from the movement ledger, not from today's balance.
 * Every status that holds physical stock is accrued, because quarantined and damaged goods still occupy space.
 * Free days reduce the chargeable quantity of per-unit storage; a fixed pallet/bin fee is accrued per occupied position (see V617).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseStorageAccrualService {

    private final WhseStorageAccrualRepository accrualRepository;
    private final WhseClientRepository clientRepository;
    private final WhseItemRepository itemRepository;
    private final WhseProfileRepository profileRepository;
    private final za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository inventoryRepository;
    private final WhseTariffBookSource tariffSource;
    private final WhseLocationAccrualRepository locationAccrualRepository;

    /** Calendar days are cut in this zone (receipt day and dispatch day both count in full). */
    @org.springframework.beans.factory.annotation.Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String billingZone = "Africa/Johannesburg";

    public java.time.ZoneId zone() { return java.time.ZoneId.of(billingZone); }

    /** Optional so the service can be built without it; Spring injects it. Without it every day bills (the standard). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhseBillingDayService billingDays;

    /** @return number of accrual rows written (per-unit storage rows plus fixed pallet/bin fee rows) */
    @Transactional
    public int accrueForTenant(UUID tenantId, LocalDate date) {
        WhseProfile profile = profileRepository.findByTenantId(tenantId).orElse(null);
        Set<UUID> done = new HashSet<>(accrualRepository.findAccruedInventoryIds(tenantId, date));
        Map<UUID, WhseClient> clients = new HashMap<>();
        Map<UUID, Map<UUID, WhseItem>> itemsByClient = new HashMap<>();
        List<WhseStorageAccrual> toSave = new ArrayList<>();
        int unrated = 0;

        WhseTariffBook book = tariffSource.bookFor(tenantId);
        java.time.ZoneId zone = zone();
        Map<UUID, java.math.BigDecimal> held = new HashMap<>();
        for (Object[] row : accrualRepository.billableQuantities(tenantId, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant())) {
            held.put((UUID) row[0], (java.math.BigDecimal) row[1]);
        }
        // Free days and fixed fees look at every position held that day, including ones already accrued on an earlier run,
        // so a re-run reaches the same answer.
        List<WhseInventory> positions = new ArrayList<>();
        inventoryRepository.findAllById(held.keySet()).forEach(positions::add);
        positions.sort(java.util.Comparator.comparing(p -> p.getId().toString()));
        // A contract may replace the standard (every calendar day) with, say, weekdays only; on a day it does not bill, nothing accrues for that client.
        if (billingDays != null) {
            WhseBillingDayService.Book days = billingDays.book(za.co.handyflow.platform.shared.TenantId.of(tenantId));
            positions.removeIf(p -> !days.billable(p.getClientId(), date));
        }
        Map<UUID, java.math.BigDecimal> free = freeQuantities(tenantId, date, zone, book, positions, held);

        for (WhseInventory position : positions) {
            if (done.contains(position.getId())) continue;
            WhseClient client = clients.computeIfAbsent(position.getClientId(),
                    id -> clientRepository.findById(id).orElse(null));
            if (client == null) continue;
            Map<UUID, WhseItem> items = itemsByClient.computeIfAbsent(position.getClientId(),
                    id -> itemRepository.findAllActiveForClient(tenantId, id).stream()
                            .collect(java.util.stream.Collectors.toMap(WhseItem::getId, i -> i, (a, b) -> a)));
            WhseItem item = items.get(position.getItemId());
            WhseTariffBook.Rate r = book.resolve(position.getClientId(), position.getItemId(), date,
                    item == null ? null : item.getStorageRatePerUnitPerMonth(), client.getStorageRatePerUnitPerMonth(),
                    profile == null ? null : profile.getDefaultStorageRatePerUnitPerMonth());
            if (r == null) {
                unrated++;
                continue;
            }
            toSave.add(WhseStorageAccrual.of(position, date, held.get(position.getId()), r.rate(), r.source())
                    .withFreeQty(free.getOrDefault(position.getId(), java.math.BigDecimal.ZERO)).pricedBy(r.tariffId()));
        }
        accrualRepository.saveAll(toSave);

        List<WhseLocationAccrual> locationRows = locationAccruals(tenantId, date, book, positions, held, free);
        locationAccrualRepository.saveAll(locationRows);

        if (unrated > 0) {
            log.warn("[Warehousing] {} stocked position(s) had no storage rate for tenant={} on {}; no accrual written",
                    unrated, tenantId, date);
        }
        log.info("[Warehousing] Storage accrual tenant={} date={} written={} locationFees={}", tenantId, date, toSave.size(), locationRows.size());
        return toSave.size() + locationRows.size();
    }

    /**
     * Free quantity per position. Stock received in the client's free window is the newest stock held (first-in-first-out per
     * client, item and lot), so each (client, item, lot) has min(held, received in window) free, shared out over its positions.
     */
    private Map<UUID, java.math.BigDecimal> freeQuantities(UUID tenantId, LocalDate date, java.time.ZoneId zone, WhseTariffBook book,
                                                           List<WhseInventory> positions, Map<UUID, java.math.BigDecimal> held) {
        Map<UUID, java.math.BigDecimal> out = new HashMap<>();
        Map<UUID, Integer> daysByClient = new HashMap<>();
        for (WhseInventory p : positions) {
            daysByClient.computeIfAbsent(p.getClientId(), c -> book.termsFor(c, date).freeDays());
        }
        // One query per distinct window length (usually one).
        Map<Integer, Map<String, java.math.BigDecimal>> receivedByWindow = new HashMap<>();
        for (int n : new HashSet<>(daysByClient.values())) {
            if (n <= 0) continue;
            Map<String, java.math.BigDecimal> m = new HashMap<>();
            for (Object[] row : accrualRepository.receivedBetween(tenantId, date.minusDays(n - 1L).atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant())) {
                m.merge(key((UUID) row[0], (UUID) row[1], (UUID) row[2]), (java.math.BigDecimal) row[3], java.math.BigDecimal::add);
            }
            receivedByWindow.put(n, m);
        }
        Map<String, java.math.BigDecimal> remaining = new HashMap<>();
        Map<String, java.math.BigDecimal> totalHeld = new HashMap<>();
        for (WhseInventory p : positions) {
            totalHeld.merge(key(p.getClientId(), p.getItemId(), p.getLotId()), held.get(p.getId()), java.math.BigDecimal::add);
        }
        for (WhseInventory p : positions) {
            int n = daysByClient.get(p.getClientId());
            if (n <= 0) continue;
            String k = key(p.getClientId(), p.getItemId(), p.getLotId());
            java.math.BigDecimal left = remaining.computeIfAbsent(k, kk ->
                    WhseTariffBook.freeQuantity(totalHeld.get(kk), receivedByWindow.get(n).get(kk)));
            java.math.BigDecimal mine = held.get(p.getId()).min(left);
            remaining.put(k, left.subtract(mine));
            if (mine.signum() > 0) out.put(p.getId(), mine);
        }
        return out;
    }

    /** One fixed-fee row per client per occupied position, for clients whose terms carry a pallet/bin fee. */
    private List<WhseLocationAccrual> locationAccruals(UUID tenantId, LocalDate date, WhseTariffBook book, List<WhseInventory> positions,
                                                       Map<UUID, java.math.BigDecimal> held, Map<UUID, java.math.BigDecimal> free) {
        Set<String> done = new HashSet<>();
        for (Object[] row : locationAccrualRepository.findAccruedKeys(tenantId, date)) {
            done.add(row[0] + "|" + row[1]);
        }
        Map<String, List<WhseInventory>> byPlace = new java.util.LinkedHashMap<>();
        for (WhseInventory p : positions) {
            byPlace.computeIfAbsent(p.getClientId() + "|" + p.getLocationId(), k -> new ArrayList<>()).add(p);
        }
        List<WhseLocationAccrual> rows = new ArrayList<>();
        for (Map.Entry<String, List<WhseInventory>> e : byPlace.entrySet()) {
            if (done.contains(e.getKey())) continue;
            WhseInventory first = e.getValue().get(0);
            java.math.BigDecimal fee = book.termsFor(first.getClientId(), date).locationFeePerMonth();
            if (fee == null || fee.signum() <= 0) continue;
            boolean allFree = e.getValue().stream().allMatch(p ->
                    free.getOrDefault(p.getId(), java.math.BigDecimal.ZERO).compareTo(held.get(p.getId())) >= 0);
            rows.add(WhseLocationAccrual.of(tenantId, first.getClientId(), first.getLocationId(), date, fee, allFree));
        }
        return rows;
    }

    private static String key(UUID client, UUID item, UUID lot) {
        return client + "|" + item + "|" + lot;
    }
}

package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.model.WhseStorageAccrual;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;
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

    /** Calendar days are cut in this zone (receipt day and dispatch day both count in full). */
    @org.springframework.beans.factory.annotation.Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String billingZone = "Africa/Johannesburg";

    public java.time.ZoneId zone() { return java.time.ZoneId.of(billingZone); }

    /** @return number of accrual rows written */
    @Transactional
    public int accrueForTenant(UUID tenantId, LocalDate date) {
        WhseProfile profile = profileRepository.findByTenantId(tenantId).orElse(null);
        Set<UUID> done = new HashSet<>(accrualRepository.findAccruedInventoryIds(tenantId, date));
        Map<UUID, WhseClient> clients = new HashMap<>();
        Map<UUID, Map<UUID, WhseItem>> itemsByClient = new HashMap<>();
        List<WhseStorageAccrual> toSave = new ArrayList<>();
        int unrated = 0;

        java.time.ZoneId zone = zone();
        Map<UUID, java.math.BigDecimal> held = new HashMap<>();
        for (Object[] row : accrualRepository.billableQuantities(tenantId, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant())) {
            held.put((UUID) row[0], (java.math.BigDecimal) row[1]);
        }
        held.keySet().removeAll(done);
        for (WhseInventory position : inventoryRepository.findAllById(held.keySet())) {
            WhseClient client = clients.computeIfAbsent(position.getClientId(),
                    id -> clientRepository.findById(id).orElse(null));
            if (client == null) continue;
            Map<UUID, WhseItem> items = itemsByClient.computeIfAbsent(position.getClientId(),
                    id -> itemRepository.findAllActiveForClient(tenantId, id).stream()
                            .collect(java.util.stream.Collectors.toMap(WhseItem::getId, i -> i, (a, b) -> a)));
            Resolved r = resolve(items.get(position.getItemId()), client, profile);
            if (r == null) {
                unrated++;
                continue;
            }
            toSave.add(WhseStorageAccrual.of(position, date, held.get(position.getId()), r.rate, r.source));
        }
        accrualRepository.saveAll(toSave);
        if (unrated > 0) {
            log.warn("[Warehousing] {} stocked position(s) had no storage rate for tenant={} on {}; no accrual written",
                    unrated, tenantId, date);
        }
        log.info("[Warehousing] Storage accrual tenant={} date={} written={}", tenantId, date, toSave.size());
        return toSave.size();
    }

    record Resolved(java.math.BigDecimal rate, String source) {}

    /** Same order as invoicing always used: item, then client, then the warehouse default. Null when none is set. */
    static Resolved resolve(WhseItem item, WhseClient client, WhseProfile profile) {
        if (item != null && item.getStorageRatePerUnitPerMonth() != null) {
            return new Resolved(item.getStorageRatePerUnitPerMonth(), "ITEM");
        }
        if (client.getStorageRatePerUnitPerMonth() != null) {
            return new Resolved(client.getStorageRatePerUnitPerMonth(), "CLIENT");
        }
        if (profile != null && profile.getDefaultStorageRatePerUnitPerMonth() != null) {
            return new Resolved(profile.getDefaultStorageRatePerUnitPerMonth(), "PROFILE");
        }
        return null;
    }
}

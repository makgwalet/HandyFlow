package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelDepotSupplyShare;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotSupplyShareRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Share;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.ShareLine;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Splits a forecourt's supply across several supplying depots with percentages. The set is replaced as a whole and must total 100.
 * An empty set removes the split, and the depot goes back to its single supplier. Each supplier must be an active depot of this company, not the
 * depot itself, and must not be supplied (through its own single-supplier chain) by this depot.
 */
@Service
@RequiredArgsConstructor
public class FuelDepotSupplyShareService {

    private final FuelDepotSupplyShareRepository shares;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public List<Share> get(TenantId tenantId, UUID depotId) {
        findDepot(tenantId, depotId);
        Map<UUID, String> names = names(tenantId);
        List<Share> out = new ArrayList<>();
        for (FuelDepotSupplyShare s : shares.findByDepot(tenantId, depotId)) out.add(new Share(s.getSupplyingDepotId(), names.getOrDefault(s.getSupplyingDepotId(), "Inactive depot"), s.getPercentage()));
        return out;
    }

    @Transactional
    public List<Share> replace(TenantId tenantId, UUID depotId, List<ShareLine> lines) {
        findDepot(tenantId, depotId);
        List<ShareLine> in = lines == null ? List.of() : lines;
        Set<UUID> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ShareLine l : in) {
            if (l.supplyingDepotId() == null) throw new IllegalArgumentException("Choose a supplying depot for every line.");
            if (!seen.add(l.supplyingDepotId())) throw new IllegalArgumentException("A depot can appear only once in the split.");
            if (l.supplyingDepotId().equals(depotId)) throw new IllegalArgumentException("A depot cannot supply itself.");
            if (l.percentage() == null || l.percentage().signum() <= 0 || l.percentage().compareTo(BigDecimal.valueOf(100)) > 0)
                throw new IllegalArgumentException("Each percentage must be above 0 and at most 100.");
            findDepot(tenantId, l.supplyingDepotId());
            if (chainReaches(tenantId, l.supplyingDepotId(), depotId)) throw new IllegalArgumentException("That would make the supply chain loop back on itself.");
            total = total.add(l.percentage());
        }
        if (!in.isEmpty() && total.compareTo(BigDecimal.valueOf(100)) != 0)
            throw new IllegalArgumentException("The percentages must total 100. They total " + total.stripTrailingZeros().toPlainString() + ".");
        shares.deleteByDepot(tenantId, depotId);
        for (ShareLine l : in) shares.save(FuelDepotSupplyShare.of(tenantId, depotId, l.supplyingDepotId(), l.percentage()));
        return get(tenantId, depotId);
    }

    /** True when following single-supplier links up from {@code start} reaches {@code target}. */
    private boolean chainReaches(TenantId tenantId, UUID start, UUID target) {
        Set<UUID> seen = new HashSet<>();
        UUID cur = start;
        while (cur != null && seen.add(cur)) {
            if (cur.equals(target)) return true;
            FuelDepot d = depots.findActiveById(tenantId, cur).orElse(null);
            cur = d == null ? null : d.getSupplyingDepotId();
        }
        return false;
    }

    private Map<UUID, String> names(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        for (FuelDepot d : depots.findAllActive(tenantId)) m.put(d.getId(), d.getName());
        return m;
    }

    private FuelDepot findDepot(TenantId tenantId, UUID id) {
        return depots.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Depot", id.toString()));
    }
}

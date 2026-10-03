package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.application.FuelFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelDispatchRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class FuelFacadeImpl implements FuelFacade {

    private static final int MAX_ROWS = 1000;

    private final FuelDispatchRepository dispatchRepository;
    private final FuelTankRepository tankRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OwnDispatch> findOwnDispatches(TenantId tenantId, Instant from, Instant to, int maxRows) {
        int limit = Math.min(Math.max(maxRows, 1), MAX_ROWS);
        return toViews(dispatchRepository.findOwnBetween(tenantId, from, to, PageRequest.of(0, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OwnDispatch> findOwnDispatch(TenantId tenantId, UUID dispatchId) {
        return dispatchRepository.findOwnById(tenantId, dispatchId).map(d -> toViews(List.of(d)).get(0));
    }

    /** Tank names are looked up once for the whole batch. */
    private List<OwnDispatch> toViews(List<FuelDispatch> dispatches) {
        Set<UUID> tankIds = new HashSet<>();
        for (FuelDispatch d : dispatches) if (d.getTankId() != null) tankIds.add(d.getTankId());
        Map<UUID, String> tankNames = new HashMap<>();
        if (!tankIds.isEmpty()) for (FuelTank t : tankRepository.findAllById(tankIds)) tankNames.put(t.getId(), t.getName());
        return dispatches.stream()
                .map(d -> new OwnDispatch(d.getId(), d.getDispatchedAt(), d.getTankId(), tankNames.get(d.getTankId()), d.getVehicleId(), d.getAssetId(),
                        d.getRecipientName(), d.getLitresDispensed(), d.getCostPerLitreAtSale(), d.getHoursReading()))
                .toList();
    }
}

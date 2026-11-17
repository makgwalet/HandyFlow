package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierEvent;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierEventRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierEventResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only supplier audit trail. Joins the caller's transaction so an event and the change it describes commit together. */
@Service
@RequiredArgsConstructor
public class SupplierAuditService {

    private final ScSupplierEventRepository eventRepo;

    @Transactional
    public void record(UUID tenantId, UUID supplierId, String eventType, String fromStatus, String toStatus,
                       String detail, UUID actor, String actorName) {
        eventRepo.save(ScSupplierEvent.of(tenantId, supplierId, eventType, fromStatus, toStatus, detail, actor, actorName));
    }

    @Transactional(readOnly = true)
    public boolean happenedWithin(UUID tenantId, UUID supplierId, String eventType, Duration window) {
        return eventRepo.countSince(tenantId, supplierId, eventType, Instant.now().minus(window)) > 0;
    }

    @Transactional(readOnly = true)
    public List<SupplierEventResponse> list(TenantId tenantId, UUID supplierId) {
        return eventRepo.findAllForSupplier(tenantId.getValue(), supplierId).stream()
                .map(e -> new SupplierEventResponse(e.getId(), e.getEventType(), e.getFromStatus(), e.getToStatus(),
                        e.getDetail(), e.getActorName(), e.getCreatedAt()))
                .toList();
    }
}

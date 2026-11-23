package za.co.handyflow.platform.agriculture.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.*;
import za.co.handyflow.platform.agriculture.domain.rules.AgAuditRules;
import za.co.handyflow.platform.agriculture.dto.AuditResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only: who added a record and who last changed it. A record of another business is reported as not found. */
@Service
public class AgAuditService {

    private static final Map<String, Class<? extends AgAudited>> ENTITIES = Map.ofEntries(
            Map.entry("animals", AgAnimal.class),
            Map.entry("groups", AgGroup.class),
            Map.entry("health-events", AgHealthEvent.class),
            Map.entry("breeding-records", AgBreedingRecord.class),
            Map.entry("inventory-items", AgInventoryItem.class),
            Map.entry("crop-cycles", AgCropCycle.class),
            Map.entry("weights", AgWeightRecord.class),
            Map.entry("mortality", AgMortalityRecord.class),
            Map.entry("movements", AgMovementRecord.class),
            Map.entry("feed-records", AgFeedRecord.class),
            Map.entry("scouting", AgScoutingRecord.class),
            Map.entry("harvests", AgHarvestRecord.class),
            Map.entry("input-applications", AgInputApplication.class));

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public AuditResponse get(TenantId tenantId, String type, UUID id) {
        AgAudited record = em.find(ENTITIES.get(AgAuditRules.requireType(type)), id);
        if (record == null || !record.getTenantId().equals(tenantId)) throw new ResourceNotFoundException("AgRecord", id.toString());
        return toResponse(record);
    }

    /** The stamps of several records of one kind, by id. Ids that are not found, or belong to another business, are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, AuditResponse> getMany(TenantId tenantId, String type, List<UUID> ids) {
        Class<? extends AgAudited> cls = ENTITIES.get(AgAuditRules.requireType(type));
        List<UUID> wanted = AgAuditRules.requireIds(ids);
        Map<UUID, AuditResponse> out = new LinkedHashMap<>();
        for (AgAudited record : em.createQuery("SELECT e FROM " + cls.getSimpleName() + " e WHERE e.id IN :ids", cls).setParameter("ids", wanted).getResultList()) {
            if (record.getTenantId().equals(tenantId)) out.put(record.getId(), toResponse(record));
        }
        return out;
    }

    /** A record that is only ever added has no "last changed" to report. */
    private static AuditResponse toResponse(AgAudited record) {
        AgAuditStamp s = record.getAudit();
        boolean editable = record.getUpdatedAt() != null;
        return new AuditResponse(s == null ? null : s.getCreatedByName(), record.getCreatedAt(), editable && s != null ? s.getUpdatedByName() : null, record.getUpdatedAt());
    }
}

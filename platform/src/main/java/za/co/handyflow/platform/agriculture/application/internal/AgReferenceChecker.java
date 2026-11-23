package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgMovementRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgReferenceRules;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Looks up what a request points at, in the caller's own tenant, and hands the facts to {@link AgReferenceRules}. A missing or
 * other-tenant row is a 404; one that exists but doesn't fit (another farm, wrong sex or species) is a 400.
 */
@Component
@RequiredArgsConstructor
class AgReferenceChecker {

    private final AgFarmRepository farmRepository;
    private final AgProductionAreaRepository areaRepository;
    private final AgEnterpriseRepository enterpriseRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgAnimalRepository animalRepository;

    AgFarm requireActiveFarm(TenantId tenantId, UUID farmId) {
        AgFarm farm = farmRepository.findActiveById(tenantId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", String.valueOf(farmId)));
        AgReferenceRules.requireFarmUsable(farm.getStatus(), farm.getName());
        return farm;
    }

    void requireSpecies(TenantId tenantId, UUID speciesId) {
        AgSpecies species = speciesRepository.findActiveById(tenantId, speciesId)
                .orElseThrow(() -> new ResourceNotFoundException("Species", String.valueOf(speciesId)));
        if ("INACTIVE".equals(species.getStatus())) throw new IllegalStateException("Species " + species.getName() + " is inactive");
    }

    /** The area (if any) must be on {@code farmId} and usable; the enterprise (if any) must be on {@code farmId}. Pass null to skip one. */
    void checkPlacement(TenantId tenantId, UUID farmId, UUID areaId, UUID enterpriseId) {
        if (areaId != null) {
            AgProductionArea area = areaRepository.findActiveById(tenantId, areaId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductionArea", areaId.toString()));
            AgMovementRules.requireUsableArea(area.getFarmId(), area.getStatus(), farmId);
        }
        if (enterpriseId != null) {
            AgEnterprise enterprise = enterpriseRepository.findActiveById(tenantId, enterpriseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Enterprise", enterpriseId.toString()));
            AgReferenceRules.requireOnFarm("enterprise " + enterprise.getName(), enterprise.getFarmId(), farmId);
        }
    }

    /** Sire and dam must exist in this tenant and fit the animal (see {@link AgReferenceRules#requireParent}). */
    void checkParents(TenantId tenantId, UUID selfId, UUID sireId, UUID damId, UUID speciesId, LocalDate bornOn) {
        AgReferenceRules.requireDifferentParents(sireId, damId);
        for (String role : new String[] {"sire", "dam"}) {
            UUID parentId = "sire".equals(role) ? sireId : damId;
            if (parentId == null) continue;
            AgAnimal p = animalRepository.findActiveById(tenantId, parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", parentId.toString()));
            AgReferenceRules.requireParent(role, parentId, selfId, p.getSex(), p.getSpeciesId(), speciesId, p.getDateOfBirth(), bornOn);
        }
    }
}

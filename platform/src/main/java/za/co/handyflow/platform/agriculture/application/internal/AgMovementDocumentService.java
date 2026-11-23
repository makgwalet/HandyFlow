package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgMovementRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgMovementRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCsv;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Line;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Party;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Builds the document of identification for one sale or transfer out from the movement, the farm and the animal or group. */
@Service
@RequiredArgsConstructor
public class AgMovementDocumentService {

    private final AgMovementRecordRepository movementRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgFarmRepository farmRepository;
    private final AgSaleDocumentPdf pdf;

    public record SaleDocument(String fileName, byte[] bytes) {}

    @Transactional(readOnly = true)
    public SaleDocument saleDocument(TenantId tenantId, UUID movementId) {
        AgMovementRecord m = movementRepository.findByTenantAndId(tenantId, movementId)
                .orElseThrow(() -> new ResourceNotFoundException("MovementRecord", movementId.toString()));
        AgSaleDocumentRules.requireDocumentable(m.getMovementType());
        if (m.getFromFarmId() == null) throw new IllegalStateException("This movement has no farm recorded, so no document can be made");
        AgFarm farm = farmRepository.findActiveById(tenantId, m.getFromFarmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", m.getFromFarmId().toString()));

        Line line;
        if (m.getAnimalId() != null) {
            AgAnimal a = animalRepository.findActiveById(tenantId, m.getAnimalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", m.getAnimalId().toString()));
            line = new Line(a.getTagNumber(), AgSaleDocumentRules.animalDescription(speciesName(tenantId, a.getSpeciesId()), a.getSex(), a.getBreed(),
                    a.getDateOfBirth(), a.isEstimatedAge()), 1);
        } else {
            AgGroup g = groupRepository.findActiveById(tenantId, m.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group", m.getGroupId().toString()));
            int head = m.getCountMoved() != null ? m.getCountMoved() : g.getInitialCount() == null ? 0 : g.getInitialCount();
            line = new Line("Batch " + g.getBatchNumber(), AgSaleDocumentRules.groupDescription(speciesName(tenantId, g.getSpeciesId()), g.getBreed()), head);
        }

        AgSaleDocumentRules.Document doc = AgSaleDocumentRules.build(m.getId(), m.getMovementDate(), m.getMovementType(), farm.getName(),
                farm.getRegistrationNumber(), farm.getBrandMark(), farm.getEarMark(), farm.getMarkRegistrationNumber(),
                new Party(farm.getOwnerName(), null, farm.getPhysicalAddress()),
                new Party(m.getBuyerName(), m.getBuyerIdNumber(), m.getBuyerAddress()), m.getDestination(), m.getVehicleRegistration(), List.of(line));
        return new SaleDocument(AgCsv.fileName("document-of-identification", doc.number(), m.getMovementDate(), "pdf"), pdf.render(doc));
    }

    private String speciesName(TenantId tenantId, UUID speciesId) {
        return speciesRepository.findActiveById(tenantId, speciesId).map(AgSpecies::getName).orElse(null);
    }
}

package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimalTagHistory;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalTagHistoryRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgBirthRules;
import za.co.handyflow.platform.agriculture.dto.ReplaceTagRequest;
import za.co.handyflow.platform.agriculture.dto.TagHistoryResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * A newborn gets a temporary tag at birth; this puts the permanent tag in its place and keeps the old one in a history. Only a
 * temporary tag can be replaced here: an ordinary tag is part of the animal's identity (and of the documents already issued for it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgAnimalTagService {

    private final AgAnimalRepository animalRepository;
    private final AgAnimalTagHistoryRepository historyRepository;

    @Transactional
    public AgAnimal replaceTemporaryTag(TenantId tenantId, UUID animalId, ReplaceTagRequest req) {
        AgAnimal animal = animalRepository.findActiveById(tenantId, animalId)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
        AgBirthRules.requireTemporaryTag(animal.isTagTemporary());
        String newTag = AgBirthRules.requireNewTag(animal.getTagNumber(), req.tagNumber());
        if (animalRepository.existsActiveByFarmAndTagNumber(tenantId, animal.getFarmId(), newTag)) {
            throw new IllegalArgumentException("An animal with tag number '" + newTag + "' already exists on this farm");
        }
        String oldTag = animal.getTagNumber();
        animal.replaceTag(newTag);
        historyRepository.save(AgAnimalTagHistory.create(tenantId, animalId, oldTag, newTag,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
        log.info("Animal tag replaced id={} old={} new={} tenant={}", animalId, oldTag, newTag, tenantId.getValue());
        return animal;
    }

    @Transactional(readOnly = true)
    public List<TagHistoryResponse> history(TenantId tenantId, UUID animalId) {
        animalRepository.findActiveById(tenantId, animalId)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", animalId.toString()));
        return historyRepository.findForAnimal(tenantId, animalId).stream()
                .map(h -> new TagHistoryResponse(h.getId(), h.getAnimalId(), h.getOldTag(), h.getNewTag(), h.getChangedByName(), h.getChangedAt()))
                .toList();
    }
}

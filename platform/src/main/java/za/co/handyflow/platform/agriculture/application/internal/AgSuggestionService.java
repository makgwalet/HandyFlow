package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFeedRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgSuggestionRules;
import za.co.handyflow.platform.agriculture.dto.SuggestionsResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-only: the breeds and feed types a farm already uses, across all its records. */
@Service
@RequiredArgsConstructor
public class AgSuggestionService {

    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgFeedRecordRepository feedRecordRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final AgReferenceChecker references;

    @Transactional(readOnly = true)
    public SuggestionsResponse forFarm(TenantId tenantId, UUID farmId) {
        references.requireActiveFarm(tenantId, farmId);
        List<Object[]> breeds = new ArrayList<>(animalRepository.findDistinctBreedsForFarm(tenantId, farmId));
        breeds.addAll(groupRepository.findDistinctBreedsForFarm(tenantId, farmId));
        List<String> feeds = new ArrayList<>(feedRecordRepository.findDistinctFeedTypesForFarm(tenantId, farmId));
        feeds.addAll(inventoryItemRepository.findActiveFeedNamesForFarm(tenantId, farmId));
        return new SuggestionsResponse(AgSuggestionRules.breedsBySpecies(breeds), AgSuggestionRules.tidy(feeds));
    }
}

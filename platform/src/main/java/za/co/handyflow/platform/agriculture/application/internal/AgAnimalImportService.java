package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgProductionAreaRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSpeciesRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgAnimalImportRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgAnimalImportRules.Result;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.dto.AnimalImportRequest;
import za.co.handyflow.platform.agriculture.dto.AnimalImportResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creates animals from CSV rows the screen has read. Every row is checked here ({@link AgAnimalImportRules}), a tag already on
 * the farm is skipped and never overwritten, and a dry run creates nothing. Rows that pass are created in one transaction, so a
 * failure part-way leaves no half-imported file. This is a new class so {@code AgAnimalService}'s constructor is unchanged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgAnimalImportService {

    private static final int LOOKUP_ROWS = 500;

    private final AgAnimalRepository animalRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgProductionAreaRepository areaRepository;
    private final AgReferenceChecker references;

    @Transactional
    public AnimalImportResponse importAnimals(TenantId tenantId, UUID farmId, AnimalImportRequest req) {
        references.requireActiveFarm(tenantId, farmId);
        List<AgAnimalImportRules.Row> rows = req.rows() == null ? List.of() : req.rows().stream()
                .map(r -> new AgAnimalImportRules.Row(r.tagNumber(), r.name(), r.species(), r.sex(), r.breed(), r.dateOfBirth(),
                        r.area(), r.acquisitionType(), r.acquisitionDate()))
                .toList();
        AgAnimalImportRules.requireRowCount(rows.size());

        // every tag on the farm, not just those that look like the file's: the unique index is on the exact tag, the check is not case-sensitive
        Set<String> existing = new HashSet<>();
        for (String tag : animalRepository.findTagNumbersStartingWith(tenantId, farmId, "")) existing.add(tag.toLowerCase(Locale.ROOT));
        Map<String, AgAnimalImportRules.SpeciesInfo> species = new HashMap<>();
        for (AgSpecies s : speciesRepository.findAllActive(tenantId, PageRequest.of(0, LOOKUP_ROWS)).getContent())
            species.putIfAbsent(s.getName().trim().toLowerCase(Locale.ROOT), new AgAnimalImportRules.SpeciesInfo(s.getId(), s.getTrackingMode(), s.getStatus()));
        Map<String, AgAnimalImportRules.AreaInfo> areas = new HashMap<>();
        for (AgProductionArea a : areaRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, LOOKUP_ROWS)).getContent())
            areas.putIfAbsent(a.getName().trim().toLowerCase(Locale.ROOT), new AgAnimalImportRules.AreaInfo(a.getId(), a.getStatus()));

        List<Result> results = AgAnimalImportRules.plan(rows, new AgAnimalImportRules.Context(existing, species, areas, AgDates.today()));

        if (!req.dryRun()) {
            List<AgAnimal> created = new ArrayList<>();
            for (Result r : results) {
                if (!AgAnimalImportRules.OK.equals(r.status())) continue;
                AgAnimalImportRules.Parsed p = r.parsed();
                created.add(AgAnimal.create(tenantId, farmId, p.areaId(), null, p.speciesId(), p.tagNumber(), p.name(), p.breed(), p.sex(),
                        p.dateOfBirth(), false, null, null, p.acquisitionType(), p.acquisitionDate(), null));
            }
            animalRepository.saveAll(created);
            log.info("Animal import farm={} created={} total={} tenant={}", farmId, created.size(), results.size(), tenantId.getValue());
        }

        return new AnimalImportResponse(req.dryRun(), results.size(),
                AgAnimalImportRules.count(results, AgAnimalImportRules.OK),
                AgAnimalImportRules.count(results, AgAnimalImportRules.DUPLICATE),
                AgAnimalImportRules.count(results, AgAnimalImportRules.ERROR),
                results.stream().map(r -> new AnimalImportResponse.RowResult(r.line(), r.tagNumber(), r.status(), r.message())).toList());
    }
}

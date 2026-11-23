package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Herd;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Spray;
import za.co.handyflow.platform.agriculture.domain.rules.AgRegisterRules.Treatment;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The herd, treatment and spray registers of one farm as CSV. Rows are capped, and the response says so in the file name only when asked. */
@Service
@RequiredArgsConstructor
public class AgRegisterService {

    /** The most rows a register returns. A farm with more is asked to narrow the dates. */
    public static final int MAX_ROWS = 20_000;

    private final AgFarmRepository farmRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgSpeciesRepository speciesRepository;
    private final AgHealthEventRepository healthEventRepository;
    private final AgInputApplicationRepository inputApplicationRepository;
    private final AgCropCycleRepository cropCycleRepository;

    public record Register(String fileName, String csv) {}

    @Transactional(readOnly = true)
    public Register herd(TenantId tenantId, UUID farmId) {
        AgFarm farm = farm(tenantId, farmId);
        List<AgAnimal> animals = animalRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, MAX_ROWS)).getContent();
        List<AgGroup> groups = groupRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, MAX_ROWS)).getContent();
        Map<UUID, String> species = speciesNames(tenantId);
        Map<UUID, String> tags = animals.stream().collect(Collectors.toMap(AgAnimal::getId, AgAnimal::getTagNumber, (a, b) -> a));
        List<Herd> rows = new java.util.ArrayList<>();
        for (AgAnimal a : animals) {
            rows.add(new Herd("Animal", a.getTagNumber(), a.getName(), species.get(a.getSpeciesId()), a.getBreed(), a.getSex(), 1,
                    a.getDateOfBirth(), a.isEstimatedAge(), a.getStatus(), a.isBreedingStock(), a.getAcquisitionType(),
                    a.getAcquisitionDate(), a.getAcquisitionCost(), a.getSireId() == null ? null : tags.get(a.getSireId()),
                    a.getDamId() == null ? null : tags.get(a.getDamId())));
        }
        for (AgGroup g : groups) {
            rows.add(new Herd("Group", g.getBatchNumber(), null, species.get(g.getSpeciesId()), g.getBreed(), null, g.getCurrentCount(),
                    g.getOriginDate(), false, g.getStatus(), false, g.getAcquisitionType(), null, null, null, null));
        }
        return new Register(za.co.handyflow.platform.agriculture.domain.rules.AgCsv.fileName("herd-register", farm.getName(), AgDates.today(), "csv"),
                AgRegisterRules.herdCsv(farm.getName(), farm.getBrandMark(), farm.getEarMark(), rows));
    }

    @Transactional(readOnly = true)
    public Register treatments(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to) {
        AgFarm farm = farm(tenantId, farmId);
        LocalDate end = to != null ? to : AgDates.today();
        LocalDate start = from != null ? from : end.minusYears(10);
        requireRange(start, end);
        Map<UUID, String> subjects = new HashMap<>();
        animalRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, MAX_ROWS)).forEach(a -> subjects.put(a.getId(), "Animal " + a.getTagNumber()));
        groupRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, MAX_ROWS)).forEach(g -> subjects.put(g.getId(), "Batch " + g.getBatchNumber()));
        List<Treatment> rows = healthEventRepository.findForFarmBetween(tenantId, farmId, start, end, PageRequest.of(0, MAX_ROWS)).stream()
                .map(e -> new Treatment(e.getEventDate(), subjects.getOrDefault(e.getAnimalId() != null ? e.getAnimalId() : e.getGroupId(), "(removed)"),
                        e.getEventType(), e.getDescription(), e.getProductUsed(), e.getDosage(), e.getAdministeredByName(), e.getVeterinarian(),
                        e.getWithdrawalPeriodDays(), e.getStatus(), e.getNotes()))
                .toList();
        return new Register(za.co.handyflow.platform.agriculture.domain.rules.AgCsv.fileName("treatment-register", farm.getName(), end, "csv"),
                AgRegisterRules.treatmentCsv(farm.getName(), rows));
    }

    @Transactional(readOnly = true)
    public Register sprays(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to) {
        AgFarm farm = farm(tenantId, farmId);
        LocalDate end = to != null ? to : AgDates.today();
        LocalDate start = from != null ? from : end.minusYears(10);
        requireRange(start, end);
        Map<UUID, String> cycles = cropCycleRepository.findAllActiveForFarm(tenantId, farmId, PageRequest.of(0, MAX_ROWS)).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getCycleName() == null ? "" : c.getCycleName(), (a, b) -> a));
        List<Spray> rows = inputApplicationRepository.findForFarmBetween(tenantId, farmId, start, end, PageRequest.of(0, MAX_ROWS)).stream()
                .map(a -> new Spray(a.getApplicationDate(), cycles.getOrDefault(a.getCropCycleId(), "(removed)"), a.getInputType(), a.getProductUsed(),
                        a.getQuantityApplied(), a.getUnitOfMeasure(), a.getApplicationMethod(), a.getAppliedByName(), a.getWeatherConditions(),
                        a.getPreHarvestIntervalDays(), a.getNotes()))
                .toList();
        return new Register(za.co.handyflow.platform.agriculture.domain.rules.AgCsv.fileName("spray-register", farm.getName(), end, "csv"),
                AgRegisterRules.sprayCsv(farm.getName(), rows));
    }

    private AgFarm farm(TenantId tenantId, UUID farmId) {
        return farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }

    private Map<UUID, String> speciesNames(TenantId tenantId) {
        return speciesRepository.findAllActive(tenantId, PageRequest.of(0, 1000)).stream()
                .collect(Collectors.toMap(AgSpecies::getId, AgSpecies::getName, (a, b) -> a));
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("The start date must not be after the end date");
    }
}

package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.agriculture.domain.model.AgBreedingRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgAnimalRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgBreedingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCalendarRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgCalendarRules.Item;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
import za.co.handyflow.platform.agriculture.dto.CalendarItemResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The farm's own calendar: dated records the farm already keeps, read for one farm and a window of days. It writes nothing.
 * Which records count, and how the day a withdrawal period ends is worked out, are decided by the queries and by
 * {@link AgCalendarRules}; this class only loads them, names them and puts them in order.
 */
@Service
@RequiredArgsConstructor
public class AgCalendarService {

    /** Row cap for the withdrawal lookup; the treatments are read newest first, so the oldest are dropped past it. */
    private static final int WITHDRAWAL_ROW_CAP = 2000;

    private final AgHealthEventRepository healthEventRepository;
    private final AgBreedingRecordRepository breedingRecordRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final AgAnimalRepository animalRepository;
    private final AgGroupRepository groupRepository;
    private final AgReferenceChecker references;

    @Transactional(readOnly = true)
    public List<CalendarItemResponse> getCalendar(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to) {
        AgCalendarRules.requireWindow(from, to);
        references.requireActiveFarm(tenantId, farmId);

        List<AgHealthEvent> due = healthEventRepository.findCalendarForFarm(tenantId, farmId, from, to);
        List<AgHealthEvent> withdrawals = healthEventRepository.findWithdrawalsForCalendar(tenantId, farmId,
                from.minusDays(AgWithdrawalRules.MAX_DAYS), to, PageRequest.of(0, WITHDRAWAL_ROW_CAP));
        List<AgBreedingRecord> births = breedingRecordRepository.findExpectedBirthsForCalendar(tenantId, farmId, from, to);
        List<AgCropCycle> cycles = cropCycleRepository.findForCalendar(tenantId, farmId, from, to);
        List<AgScoutingRecord> followUps = scoutingRecordRepository.findFollowUpsForCalendar(tenantId, farmId, from, to);

        Names names = loadNames(tenantId, due, withdrawals, births, followUps, cycles);
        List<Item> items = new ArrayList<>();

        for (AgHealthEvent e : due) {
            String subject = names.subject(e.getAnimalId(), e.getGroupId());
            String type = AgCalendarRules.humanize(e.getEventType());
            String target = e.getAnimalId() != null ? "ANIMAL" : "GROUP";
            UUID targetId = e.getAnimalId() != null ? e.getAnimalId() : e.getGroupId();
            if (e.getNextDueDate() != null && !e.isReminderAcknowledged() && AgCalendarRules.inWindow(e.getNextDueDate(), from, to))
                items.add(new Item(e.getNextDueDate(), AgCalendarRules.HEALTH_DUE, type + " due: " + subject, e.getDescription(), target, targetId));
            if ("SCHEDULED".equals(e.getStatus()) && AgCalendarRules.inWindow(e.getEventDate(), from, to))
                items.add(new Item(e.getEventDate(), AgCalendarRules.HEALTH_SCHEDULED, type + " scheduled: " + subject, e.getDescription(), target, targetId));
        }
        for (AgHealthEvent e : withdrawals) {
            String subject = names.subject(e.getAnimalId(), e.getGroupId());
            String target = e.getAnimalId() != null ? "ANIMAL" : "GROUP";
            UUID targetId = e.getAnimalId() != null ? e.getAnimalId() : e.getGroupId();
            AgCalendarRules.Treatment t = new AgCalendarRules.Treatment(e.getEventDate(), "SCHEDULED".equals(e.getStatus()),
                    e.getWithdrawalPeriodDays(), e.getMilkWithdrawalDays(), e.getEggWithdrawalDays());
            for (AgCalendarRules.WithdrawalEnd end : AgCalendarRules.withdrawalEnds(t, from, to)) {
                String product = e.getProductUsed() == null || e.getProductUsed().isBlank() ? AgCalendarRules.humanize(e.getEventType()) : e.getProductUsed();
                items.add(new Item(end.clearOn(), AgCalendarRules.WITHDRAWAL_END,
                        capital(end.what()) + " withdrawal ends: " + subject,
                        "Clear from this day. Treated " + e.getEventDate() + " with " + product + ".", target, targetId));
            }
        }
        for (AgBreedingRecord b : births) {
            String target = b.getAnimalId() != null ? "ANIMAL" : "GROUP";
            UUID targetId = b.getAnimalId() != null ? b.getAnimalId() : b.getGroupId();
            items.add(new Item(b.getExpectedDueDate(), AgCalendarRules.BIRTH_DUE, "Birth expected: " + names.subject(b.getAnimalId(), b.getGroupId()),
                    "Mated " + b.getMatingDate(), target, targetId));
        }
        for (AgCropCycle c : cycles) {
            String name = cycleName(c);
            if ("PLANNED".equals(c.getStatus()) && AgCalendarRules.inWindow(c.getPlantingDate(), from, to))
                items.add(new Item(c.getPlantingDate(), AgCalendarRules.PLANTING, "Planting planned: " + name, c.getVariety(), "CROP_CYCLE", c.getId()));
            if (AgCalendarRules.inWindow(c.getExpectedHarvestDate(), from, to))
                items.add(new Item(c.getExpectedHarvestDate(), AgCalendarRules.HARVEST, "Harvest expected: " + name, c.getVariety(), "CROP_CYCLE", c.getId()));
        }
        for (AgScoutingRecord r : followUps) {
            String detail = r.getObservationType() + (r.getSeverity() == null ? "" : " (" + r.getSeverity().toLowerCase() + ")");
            items.add(new Item(r.getFollowUpDate(), AgCalendarRules.SCOUTING_FOLLOWUP, "Scouting follow-up: " + names.cycle(r.getCropCycleId()),
                    detail, "CROP_CYCLE", r.getCropCycleId()));
        }

        LocalDate today = AgDates.today();
        return AgCalendarRules.sorted(items).stream()
                .map(i -> new CalendarItemResponse(i.date(), i.kind(), i.title(), i.detail(), i.targetType(), i.targetId(),
                        AgCalendarRules.isOverdue(i.kind(), i.date(), today)))
                .toList();
    }

    private static String capital(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static String cycleName(AgCropCycle c) {
        return c.getCycleName() == null || c.getCycleName().isBlank() ? "Crop cycle" : c.getCycleName();
    }

    /** Animal and group labels, and crop cycle names, for the records found. */
    private final class Names {
        private final Map<UUID, String> animals = new HashMap<>();
        private final Map<UUID, String> groups = new HashMap<>();
        private final Map<UUID, String> cycles = new HashMap<>();

        String subject(UUID animalId, UUID groupId) {
            if (animalId != null) return animals.getOrDefault(animalId, "animal");
            if (groupId != null) return groups.getOrDefault(groupId, "group");
            return "";
        }

        String cycle(UUID id) { return cycles.getOrDefault(id, "Crop cycle"); }
    }

    private Names loadNames(TenantId tenantId, List<AgHealthEvent> due, List<AgHealthEvent> withdrawals, List<AgBreedingRecord> births,
                            List<AgScoutingRecord> followUps, List<AgCropCycle> cyclesInWindow) {
        Set<UUID> animalIds = new HashSet<>(), groupIds = new HashSet<>(), cycleIds = new HashSet<>();
        for (AgHealthEvent e : due) { add(animalIds, e.getAnimalId()); add(groupIds, e.getGroupId()); }
        for (AgHealthEvent e : withdrawals) { add(animalIds, e.getAnimalId()); add(groupIds, e.getGroupId()); }
        for (AgBreedingRecord b : births) { add(animalIds, b.getAnimalId()); add(groupIds, b.getGroupId()); }
        for (AgScoutingRecord r : followUps) add(cycleIds, r.getCropCycleId());
        Names names = new Names();
        for (AgAnimal a : animalRepository.findAllById(animalIds)) {
            if (!tenantId.equals(a.getTenantId())) continue;
            names.animals.put(a.getId(), a.getTagNumber() + (a.getName() == null || a.getName().isBlank() ? "" : " (" + a.getName() + ")"));
        }
        for (AgGroup g : groupRepository.findAllById(groupIds)) {
            if (!tenantId.equals(g.getTenantId())) continue;
            names.groups.put(g.getId(), "group " + g.getBatchNumber());
        }
        for (AgCropCycle c : cyclesInWindow) names.cycles.put(c.getId(), cycleName(c));
        Set<UUID> missing = new HashSet<>(cycleIds);
        missing.removeAll(names.cycles.keySet());
        for (AgCropCycle c : cropCycleRepository.findAllById(missing)) {
            if (tenantId.equals(c.getTenantId())) names.cycles.put(c.getId(), cycleName(c));
        }
        return names;
    }

    private static void add(Set<UUID> set, UUID id) { if (id != null) set.add(id); }
}

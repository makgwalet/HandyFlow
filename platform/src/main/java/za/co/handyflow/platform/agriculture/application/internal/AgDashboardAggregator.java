package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgAttentionRules;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse.*;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns pre-aggregated rows into the tenant-wide dashboard. Pure (no Spring, no repositories), so the arithmetic is unit
 * tested directly and {@link AgDashboardService} only has to fetch rows.
 * <p>
 * Every row is filtered to the farms passed in: "all farms" means all ACTIVE farms, so animals or cycles belonging to an
 * inactive farm never leak into the totals.
 */
public final class AgDashboardAggregator {

    private AgDashboardAggregator() {}

    public record FarmRow(UUID id, String name, String farmType, String province, String region,
                          Double latitude, Double longitude, BigDecimal totalHectares) {}
    public record CycleStatusRow(UUID farmId, String status, long cycles, BigDecimal hectares) {}
    public record CropAreaRow(UUID farmId, UUID cropTypeId, long cycles, BigDecimal hectares) {}
    public record AnimalRow(UUID farmId, UUID speciesId, long animals) {}
    public record GroupRow(UUID farmId, UUID speciesId, long groups, long head) {}
    public record SpeciesInfo(String name, String category) {}

    private static final List<String> STATUS_ORDER =
            List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING", "HARVESTED", "FAILED", "ABANDONED");

    static boolean inProduction(String status) {
        return "PLANTED".equals(status) || "GROWING".equals(status) || "HARVESTING".equals(status);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static AgDashboardResponse build(LocalDate asOf,
                                            List<FarmRow> farms,
                                            List<CycleStatusRow> cycleStatus,
                                            List<CropAreaRow> cropAreas,
                                            Map<UUID, String> cropNames,
                                            List<AnimalRow> animals,
                                            List<GroupRow> groups,
                                            Map<UUID, SpeciesInfo> species,
                                            List<AttentionItemResponse> attention,
                                            int attentionLimit) {
        Set<UUID> farmIds = new HashSet<>();
        for (FarmRow f : farms) farmIds.add(f.id());
        List<CycleStatusRow> cycles = cycleStatus.stream().filter(r -> farmIds.contains(r.farmId())).toList();
        List<CropAreaRow> areas = cropAreas.stream().filter(r -> farmIds.contains(r.farmId())).toList();
        List<AnimalRow> animalRows = animals.stream().filter(r -> farmIds.contains(r.farmId())).toList();
        List<GroupRow> groupRows = groups.stream().filter(r -> farmIds.contains(r.farmId())).toList();

        // ---- per-farm figures -------------------------------------------------------------------------------------
        Map<UUID, BigDecimal> haInProduction = new HashMap<>();
        Map<UUID, Long> cyclesInProduction = new HashMap<>();
        for (CycleStatusRow r : cycles) {
            if (!inProduction(r.status())) continue;
            haInProduction.merge(r.farmId(), nz(r.hectares()), BigDecimal::add);
            cyclesInProduction.merge(r.farmId(), r.cycles(), Long::sum);
        }
        Map<UUID, Long> animalsByFarm = new HashMap<>();
        for (AnimalRow r : animalRows) animalsByFarm.merge(r.farmId(), r.animals(), Long::sum);
        Map<UUID, Long> headByFarm = new HashMap<>();
        for (GroupRow r : groupRows) headByFarm.merge(r.farmId(), r.head(), Long::sum);
        Map<UUID, Integer> attentionByFarm = new HashMap<>();
        Map<UUID, Integer> urgentByFarm = new HashMap<>();
        for (AttentionItemResponse a : attention) {
            if (a.farmId() == null) continue;
            attentionByFarm.merge(a.farmId(), 1, Integer::sum);
            if (AgAttentionRules.isUrgent(a)) urgentByFarm.merge(a.farmId(), 1, Integer::sum);
        }

        List<FarmSummary> farmSummaries = farms.stream()
                .sorted(Comparator.comparing((FarmRow f) -> f.name() == null ? "" : f.name().toLowerCase()))
                .map(f -> new FarmSummary(f.id(), f.name(), f.farmType(), f.province(), f.region(), f.latitude(), f.longitude(),
                        f.totalHectares(),
                        haInProduction.getOrDefault(f.id(), BigDecimal.ZERO),
                        cyclesInProduction.getOrDefault(f.id(), 0L),
                        animalsByFarm.getOrDefault(f.id(), 0L),
                        headByFarm.getOrDefault(f.id(), 0L),
                        attentionByFarm.getOrDefault(f.id(), 0),
                        urgentByFarm.getOrDefault(f.id(), 0)))
                .toList();

        // ---- totals -----------------------------------------------------------------------------------------------
        BigDecimal totalHectares = BigDecimal.ZERO;
        int withoutHectares = 0;
        for (FarmRow f : farms) {
            if (f.totalHectares() == null || f.totalHectares().signum() <= 0) withoutHectares++;
            else totalHectares = totalHectares.add(f.totalHectares());
        }
        long animalCount = animalRows.stream().mapToLong(AnimalRow::animals).sum();
        long groupCount = groupRows.stream().mapToLong(GroupRow::groups).sum();
        long groupHead = groupRows.stream().mapToLong(GroupRow::head).sum();
        long plannedCycles = cycles.stream().filter(r -> "PLANNED".equals(r.status())).mapToLong(CycleStatusRow::cycles).sum();
        long inProductionCycles = cycles.stream().filter(r -> inProduction(r.status())).mapToLong(CycleStatusRow::cycles).sum();
        BigDecimal inProductionHa = cycles.stream().filter(r -> inProduction(r.status()))
                .map(r -> nz(r.hectares())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Totals totals = new Totals(farms.size(), totalHectares, withoutHectares, inProductionCycles, inProductionHa,
                plannedCycles, animalCount, groupCount, groupHead, animalCount + groupHead);

        // ---- farm types -------------------------------------------------------------------------------------------
        Map<String, int[]> typeCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> typeHectares = new HashMap<>();
        for (FarmRow f : farms) {
            String type = f.farmType() == null || f.farmType().isBlank() ? "UNSPECIFIED" : f.farmType();
            typeCounts.computeIfAbsent(type, k -> new int[1])[0]++;
            typeHectares.merge(type, nz(f.totalHectares()), BigDecimal::add);
        }
        List<FarmTypeCount> farmTypes = typeCounts.entrySet().stream()
                .map(e -> new FarmTypeCount(e.getKey(), e.getValue()[0], typeHectares.get(e.getKey())))
                .sorted(Comparator.comparingInt(FarmTypeCount::farmCount).reversed().thenComparing(FarmTypeCount::farmType))
                .toList();

        // ---- crops ------------------------------------------------------------------------------------------------
        Map<String, long[]> byStatusCycles = new HashMap<>();
        Map<String, BigDecimal> byStatusHa = new HashMap<>();
        for (CycleStatusRow r : cycles) {
            byStatusCycles.computeIfAbsent(r.status(), k -> new long[1])[0] += r.cycles();
            byStatusHa.merge(r.status(), nz(r.hectares()), BigDecimal::add);
        }
        List<StatusCount> byStatus = new ArrayList<>();
        for (String s : STATUS_ORDER) {
            if (byStatusCycles.containsKey(s)) byStatus.add(new StatusCount(s, byStatusCycles.get(s)[0], byStatusHa.get(s)));
        }
        Map<UUID, long[]> cropCycles = new HashMap<>();
        Map<UUID, BigDecimal> cropHa = new HashMap<>();
        for (CropAreaRow r : areas) {
            cropCycles.computeIfAbsent(r.cropTypeId(), k -> new long[1])[0] += r.cycles();
            cropHa.merge(r.cropTypeId(), nz(r.hectares()), BigDecimal::add);
        }
        List<CropArea> inProductionByCrop = cropCycles.entrySet().stream()
                .map(e -> new CropArea(e.getKey(), cropNames.getOrDefault(e.getKey(), "Unknown crop"), e.getValue()[0], cropHa.get(e.getKey())))
                .sorted(Comparator.comparing(CropArea::hectares).reversed().thenComparing(CropArea::cropName))
                .toList();

        // ---- livestock by species ---------------------------------------------------------------------------------
        Map<UUID, long[]> speciesTotals = new HashMap<>();      // [animals, groupHead]
        for (AnimalRow r : animalRows) speciesTotals.computeIfAbsent(r.speciesId(), k -> new long[2])[0] += r.animals();
        for (GroupRow r : groupRows) speciesTotals.computeIfAbsent(r.speciesId(), k -> new long[2])[1] += r.head();
        List<SpeciesCount> livestock = speciesTotals.entrySet().stream()
                .map(e -> {
                    SpeciesInfo info = species.get(e.getKey());
                    long a = e.getValue()[0], g = e.getValue()[1];
                    return new SpeciesCount(e.getKey(), info != null ? info.name() : "Unknown species", info != null ? info.category() : null, a, g, a + g);
                })
                .sorted(Comparator.comparingLong(SpeciesCount::totalHead).reversed().thenComparing(SpeciesCount::name))
                .toList();

        // ---- attention --------------------------------------------------------------------------------------------
        List<AttentionItemResponse> ranked = AgAttentionRules.ranked(attention);
        List<SeverityCount> bySeverity = AgAttentionRules.countBySeverity(ranked).entrySet().stream()
                .map(e -> new SeverityCount(e.getKey(), e.getValue())).toList();
        List<AttentionItemResponse> top = ranked.size() > attentionLimit ? ranked.subList(0, attentionLimit) : ranked;

        return new AgDashboardResponse(asOf, totals, farmTypes, farmSummaries, new CropSummary(byStatus, inProductionByCrop),
                livestock, new AttentionSummary(ranked.size(), bySeverity, List.copyOf(top)));
    }
}

package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.*;

/**
 * The audit universe laid out by risk, worked out from plain inputs so it can be tested without a database. Two views of the same latest
 * assessments: a grid of inherent risk against control risk (each scored 1 to 5 in the assessment), and a count of areas by final risk level
 * for each process area.
 */
public final class RiskHeatMap {
    private RiskHeatMap() {}

    public static final String NO_AREA = "No process area";
    static final List<String> LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public record AreaIn(UUID id, String name, String processArea) {}
    /** The latest assessment of one area. */
    public record AssessmentIn(UUID areaId, int inherent, int control, String finalRisk) {}

    /** One square of the grid: how many areas have this inherent and control score, and which. */
    public record Cell(int inherent, int control, int count, List<String> areas) {}
    public record Row(String processArea, int low, int medium, int high, int critical, int notAssessed, int total) {}
    public record HeatMap(List<Cell> cells, List<Row> byProcessArea, int assessed, int notAssessed, int total) {}

    /** Scores outside 1 to 5 are put at the nearest end, so that one bad row cannot take the screen down. */
    static int clamp(int score) { return Math.max(1, Math.min(5, score)); }

    public static HeatMap build(List<AreaIn> areas, List<AssessmentIn> latest) {
        Map<UUID, AssessmentIn> byArea = new HashMap<>();
        latest.forEach(a -> byArea.put(a.areaId(), a));

        Map<String, List<String>> grid = new HashMap<>();
        Map<String, int[]> rows = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int assessed = 0;
        for (AreaIn a : areas) {
            AssessmentIn r = byArea.get(a.id());
            String pa = a.processArea() == null || a.processArea().isBlank() ? NO_AREA : a.processArea().trim();
            int[] row = rows.computeIfAbsent(pa, k -> new int[5]);     // low, medium, high, critical, not assessed
            if (r == null) { row[4]++; continue; }
            assessed++;
            grid.computeIfAbsent(clamp(r.inherent()) + ":" + clamp(r.control()), k -> new ArrayList<>()).add(a.name());
            int i = LEVELS.indexOf(r.finalRisk() == null ? "" : r.finalRisk().toUpperCase());
            if (i >= 0) row[i]++; else row[4]++;
        }

        List<Cell> cells = new ArrayList<>();
        for (int inherent = 5; inherent >= 1; inherent--)           // highest inherent risk at the top
            for (int control = 1; control <= 5; control++) {
                List<String> names = grid.getOrDefault(inherent + ":" + control, List.of()).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
                cells.add(new Cell(inherent, control, names.size(), names));
            }
        List<Row> out = rows.entrySet().stream()
                .map(e -> new Row(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4],
                        e.getValue()[0] + e.getValue()[1] + e.getValue()[2] + e.getValue()[3] + e.getValue()[4]))
                .sorted(Comparator.comparingInt((Row r) -> -(r.critical() * 1000 + r.high())).thenComparing(Row::processArea, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new HeatMap(cells, out, assessed, areas.size() - assessed, areas.size());
    }
}

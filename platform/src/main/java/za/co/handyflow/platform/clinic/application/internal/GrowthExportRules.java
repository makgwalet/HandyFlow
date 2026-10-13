package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.GrowthDtos.GrowthChart;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.Measured;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.MeasureChart;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The growth chart as a spreadsheet file (patch 0191, CLINIC_GROWTH_EXPORT). Measurements always; z-scores and percentiles only where the
 * server computed them from an approved reference set, and the "not clinically approved" banner travels with the data. Pure.
 */
final class GrowthExportRules {
    private GrowthExportRules() {}

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Africa/Johannesburg"));

    /** One cell: quoted when needed, and a leading = + - @ (or tab / return) is defused so a spreadsheet never runs it as a formula. */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static String row(String... cells) {
        List<String> out = new ArrayList<>();
        for (String c : cells) out.add(cell(c));
        return String.join(",", out) + "\r\n";
    }

    private static String num(Double d, int places) { return d == null ? "" : String.format(Locale.ROOT, "%." + places + "f", d); }

    static String csv(String patientName, GrowthChart chart, String exportedOn) {
        StringBuilder sb = new StringBuilder();
        sb.append(row("Growth chart export"));
        sb.append(row("Patient", patientName));
        sb.append(row("Sex at birth", chart.sexAtBirth()));
        sb.append(row("Age now (months)", num(chart.currentAgeMonths(), 1)));
        sb.append(row("Exported", exportedOn));
        sb.append(row());
        sb.append(row("Measure", "Unit", "Date taken", "Age (months)", "Value", "Z-score", "Percentile"));
        for (MeasureChart m : chart.measures()) {
            for (Measured p : m.measurements()) {
                sb.append(row(m.label(), m.unit(), DAY.format(p.takenAt()), num(p.ageMonths(), 1),
                        p.value() == null ? "" : p.value().toPlainString(), num(p.zScore(), 2), num(p.percentile(), 1)));
            }
        }
        sb.append(row());
        for (MeasureChart m : chart.measures()) if (m.banner() != null && !m.banner().isBlank()) sb.append(row("Note", m.label() + ": " + m.banner()));
        for (String n : chart.notes()) sb.append(row("Note", n));
        return sb.toString();
    }

    static String fileName(String patientName, String day) {
        String base = patientName == null ? "patient" : patientName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return "growth-" + (base.isEmpty() ? "patient" : base) + "-" + day + ".csv";
    }
}

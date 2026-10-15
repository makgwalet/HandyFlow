package za.co.handyflow.platform.internalaudit.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** The report layouts a new report can start from, how the findings section is written, and the fingerprint of issued content. */
public final class ReportTemplates {
    private ReportTemplates() {}

    public record SectionSpec(String title, boolean autoFindings, String guidance) {}
    public record Template(String id, String label, List<SectionSpec> sections) {}
    public record FindingText(String title, String severity, String criteria, String condition, String consequence, String recommendation, String managementResponse) {}

    private static final Map<String, Template> ALL = new LinkedHashMap<>();
    static {
        add(new Template("STANDARD", "Standard audit report", List.of(
                new SectionSpec("Executive summary", false, "The conclusion in a paragraph, and the findings that matter most."),
                new SectionSpec("Scope and objectives", false, "What was audited, for what period, and what the audit set out to establish."),
                new SectionSpec("Approach", false, "How the work was done: tests, sampling, evidence reviewed."),
                new SectionSpec("Findings and recommendations", true, ""),
                new SectionSpec("Conclusion", false, "The overall conclusion and any limits on it."))));
        add(new Template("SHORT_FORM", "Short-form report", List.of(
                new SectionSpec("Summary", false, "What was done and the conclusion."),
                new SectionSpec("Findings and recommendations", true, ""),
                new SectionSpec("Conclusion", false, ""))));
        add(new Template("FOLLOW_UP", "Follow-up report", List.of(
                new SectionSpec("Summary", false, "What was followed up and the overall position."),
                new SectionSpec("Previous findings and the status of actions", true, ""),
                new SectionSpec("Conclusion", false, "Whether the earlier findings have been dealt with."))));
    }
    private static void add(Template t) { ALL.put(t.id(), t); }

    public static Collection<Template> all() { return ALL.values(); }
    public static Optional<Template> find(String id) { return Optional.ofNullable(id == null ? null : ALL.get(id.toUpperCase())); }

    /** Plain-text findings, worst first. Management's response is shown only when there is one. */
    public static String findingsText(List<FindingText> findings) {
        if (findings.isEmpty()) return "No findings were raised on this engagement.";
        List<String> order = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        List<FindingText> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt((FindingText f) -> {
            int i = order.indexOf(f.severity() == null ? "" : f.severity().toUpperCase());
            return i < 0 ? order.size() : i;
        }).thenComparing(FindingText::title, String.CASE_INSENSITIVE_ORDER));
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (FindingText f : sorted) {
            if (n > 1) sb.append("\n\n");
            sb.append(n++).append(". ").append(f.title()).append(" [").append(f.severity() == null ? "unrated" : f.severity().toUpperCase()).append("]");
            line(sb, "Criteria", f.criteria()); line(sb, "Condition", f.condition()); line(sb, "Consequence", f.consequence());
            line(sb, "Recommendation", f.recommendation()); line(sb, "Management response", f.managementResponse());
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String v) {
        if (v != null && !v.isBlank()) sb.append("\n").append(label).append(": ").append(v.trim());
    }

    /** SHA-256 over the title and every section in order. The same content always gives the same value; any change gives another. */
    public static String contentHash(String title, List<String[]> sectionsInOrder) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(("T\u0000" + title).getBytes(StandardCharsets.UTF_8));
            int i = 1;
            for (String[] s : sectionsInOrder) md.update(("\u0001" + i++ + "\u0000" + s[0] + "\u0000" + s[1]).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

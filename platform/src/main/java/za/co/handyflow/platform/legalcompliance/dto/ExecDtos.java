package za.co.handyflow.platform.legalcompliance.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Executive GRC overview. Every figure is a count from a register; there is deliberately no composite "compliance score". */
public final class ExecDtos {

    private ExecDtos() {}

    /** percent is null when the denominator is zero: "nothing to measure" is not shown as 0% or 100%. */
    public record Indicator(String key, String label, String formula, long numerator, long denominator, Integer percent, String tab) {}

    public record AttentionItem(String key, String severity, String text, long count, String tab) {}

    public record Overview(Instant generatedAt, Map<String, Long> metrics, List<Indicator> indicators, List<AttentionItem> attention) {}

    public record SnapshotPoint(LocalDate date, String source, String takenBy, Map<String, Long> metrics, List<Indicator> indicators) {}
}

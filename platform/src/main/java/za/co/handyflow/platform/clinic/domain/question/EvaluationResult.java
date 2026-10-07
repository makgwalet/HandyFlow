package za.co.handyflow.platform.clinic.domain.question;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Outcome of evaluating a question group against the current answers. */
public record EvaluationResult(
        Set<String> visible,
        Set<String> required,
        Set<String> disabled,
        Map<String, List<String>> warnings,
        Set<String> triggeredGroups,
        List<RedFlagResult> redFlags,
        Map<String, Object> effectiveAnswers,
        List<String> missingRequired) {

    public record RedFlagResult(String code, String label, String severity, String message) {}

    public boolean hasUrgentFlag() {
        return redFlags.stream().anyMatch(f -> "URGENT".equals(f.severity()));
    }
}

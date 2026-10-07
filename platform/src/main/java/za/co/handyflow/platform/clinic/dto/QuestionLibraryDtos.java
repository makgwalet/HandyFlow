package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestionLibraryDtos {
    private QuestionLibraryDtos() {}

    public record RuleView(String kind, Map<String, Object> expression, String message, String targetGroupCode) {}

    public record QuestionView(String code, String label, String helpText, String answerType,
                               List<Map<String, Object>> options, BigDecimal min, BigDecimal max,
                               boolean defaultVisible, boolean defaultRequired, String observationCode,
                               List<RuleView> rules) {}

    public record RedFlagView(String code, String label, String severity, Map<String, Object> expression, String message) {}

    public record GroupView(UUID id, String code, int version, String name, String category, boolean required,
                            boolean defaultEnabled, String status, boolean demo,
                            List<QuestionView> questions, List<RedFlagView> redFlags) {}

    /** Admin list row: no questions, includes governance state. */
    public record GroupSummary(UUID id, String code, int version, String name, String category, String status,
                               boolean demo, String clinicalSource, String sourceVersion,
                               UUID reviewedBy, UUID approvedBy) {}

    public record EvaluateRequest(UUID patientId, String visitType, Map<String, Object> answers) {}

    public record EvaluationView(List<String> visible, List<String> required, List<String> disabled,
                                 Map<String, List<String>> warnings, List<String> triggeredGroups,
                                 List<RedFlagView> redFlags, boolean urgent,
                                 Map<String, Object> effectiveAnswers, List<String> missingRequired,
                                 Map<String, String> problems) {}

    public record SaveAnswersRequest(Map<String, Object> answers) {}

    public record StatusChangeRequest(String status, String note) {}

    // ── Authoring ────────────────────────────────────────────────────────────

    /** Everything an author edits on one group. Question order is list order. */
    public record GroupDefinition(String name, String category, Integer minAgeMonths, Integer maxAgeMonths,
                                  List<String> sex, List<String> visitTypes, boolean defaultEnabled,
                                  String clinicalSource, String sourceVersion,
                                  List<QuestionView> questions, List<RedFlagView> redFlags) {}

    public record CreateGroupRequest(String code, GroupDefinition definition) {}

    public record GroupDefinitionView(UUID id, String code, int version, String status, boolean demo,
                                      GroupDefinition definition) {}
}

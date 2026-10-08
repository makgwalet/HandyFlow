package za.co.handyflow.platform.clinic.application.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Examination libraries (Q-6). An examination group is an ordinary question group whose category is EXAMINATION; a question may carry
 * a "normal" answer that the clinician can apply in one tap. The value comes from the clinical reviewer through authoring; this class only
 * checks that it is something the answer type can hold, so a preset can never put an impossible answer on a record. It judges nothing.
 */
final class ExamGroupRules {

    static final String CATEGORY = "EXAMINATION";

    private ExamGroupRules() {}

    static boolean isExamination(String category) {
        return category != null && CATEGORY.equalsIgnoreCase(category.trim());
    }

    /** True when a group belongs on the requested page of the consultation (examination step or the questionnaire block). */
    static boolean onPage(String category, boolean examinationPage) {
        return isExamination(category) == examinationPage;
    }

    /** Null when the preset is fine (or absent); otherwise what is wrong with it. */
    static String normalProblem(String answerType, List<Map<String, Object>> options, Object normal) {
        if (normal == null) return null;
        if (answerType == null) return "needs a valid answer type before it can have a normal answer.";
        Set<String> values = new java.util.HashSet<>();
        if (options != null) for (Map<String, Object> o : options) if (o != null && o.get("value") instanceof String v) values.add(v);
        switch (answerType) {
            case "YES_NO", "TOGGLE" -> {
                if (!(normal instanceof Boolean)) return "normal answer must be true or false.";
            }
            case "TEXT", "LONG_TEXT" -> {
                if (!(normal instanceof String s) || s.isBlank()) return "normal answer must be text.";
                if (s.length() > 500) return "normal answer is longer than 500 characters.";
            }
            case "SINGLE_SELECT", "RADIO_GROUP" -> {
                if (!(normal instanceof String s) || !values.contains(s)) return "normal answer must be one of the options.";
            }
            case "MULTI_SELECT", "CHECKLIST" -> {
                if (!(normal instanceof List<?> l) || l.isEmpty()) return "normal answer must be a list of options.";
                for (Object o : l) if (!(o instanceof String s) || !values.contains(s)) return "normal answer lists something that is not an option.";
            }
            default -> {
                return "a " + answerType + " question cannot have a normal answer (measurements and numbers are never preset).";
            }
        }
        return null;
    }
}

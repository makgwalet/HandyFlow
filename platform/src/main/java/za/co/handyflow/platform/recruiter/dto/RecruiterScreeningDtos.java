package za.co.handyflow.platform.recruiter.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class RecruiterScreeningDtos {
    private RecruiterScreeningDtos() {}

    /** What staff set: the criterion (expectedAnswer / minimumNumber) is staff-only. */
    public record QuestionInput(String text, String type, boolean required, String expectedAnswer, BigDecimal minimumNumber) {}
    public record SaveQuestionsRequest(List<QuestionInput> questions) {}
    public record StaffQuestion(UUID id, int position, String text, String type, boolean required, String expectedAnswer, BigDecimal minimumNumber) {}

    /** What an applicant sees: never the criterion. */
    public record PublicQuestion(UUID id, String text, String type, boolean required) {}

    public record AnswerInput(UUID questionId, String answer) {}

    public record AnswerView(String question, String type, boolean knockout, String answer, Boolean meetsCriteria) {}
    public record ScreeningView(UUID applicationId, int knockoutsFailed, List<AnswerView> answers) {}
}

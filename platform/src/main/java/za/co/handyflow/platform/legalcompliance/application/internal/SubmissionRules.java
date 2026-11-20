package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.ProviderSubmission;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.SubmissionView;

import java.time.LocalDate;

/** Pure rules for provider submissions. */
public final class SubmissionRules {

    private SubmissionRules() {}

    /** Unanswered submissions one relationship may have at a time, so a provider cannot flood a client's inbox. */
    public static final int OPEN_LIMIT = 20;

    public static boolean limitReached(long unanswered) { return unanswered >= OPEN_LIMIT; }

    public static SubmissionView view(ProviderSubmission s, LocalDate today) {
        return new SubmissionView(s.getId(), s.getReference(), s.getProviderClientId(), s.getProviderName(), s.getKind(), s.getArea(), s.getPriority(), s.getTitle(), s.getBody(), s.getRespondBy(),
                s.getSubmittedByName(), s.getStatus(), s.getResponse(), s.getRespondedByName(), s.getRespondedAt(), s.getCreatedAt(), s.isOverdue(today));
    }
}

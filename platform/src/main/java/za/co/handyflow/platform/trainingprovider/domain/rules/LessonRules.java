package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for learning content and progress. Lesson status handling is shared with assessment instruments (draft, approved, retired). Progress says
 * a learner finished a lesson. It is never competence, attendance or a grade, and nothing here holds a threshold.
 */
public final class LessonRules {

    private LessonRules() {
    }

    public static final String READING = "READING";
    public static final String LINK = "LINK";
    public static final String DOCUMENT = "DOCUMENT";
    public static final Set<String> KINDS = Set.of(READING, LINK, DOCUMENT);
    public static final String FINISHED = "FINISHED";
    public static final String REVERSED = "REVERSED";

    public static boolean validUrl(String url) {
        return url != null && url.trim().matches("(?i)^https?://[^\\s]+$");
    }

    public static String lessonProblem(String title, String kind, String contentText, String url, Integer position) {
        if (!CohortRules.longEnough(title, 2)) {
            return "Give the lesson a title";
        }
        if (kind == null || !KINDS.contains(kind)) {
            return "Choose reading, link or document";
        }
        if (position != null && position < 1) {
            return "The position must be 1 or more";
        }
        if (READING.equals(kind)) {
            if (!CohortRules.longEnough(contentText, 20)) {
                return "Write the reading, in at least 20 characters";
            }
            if (url != null && !url.isBlank()) {
                return "A reading has no link. Choose link or document to add one";
            }
        } else if (!validUrl(url)) {
            return "Enter a link that starts with http:// or https://";
        }
        return null;
    }

    public static String moduleProblem(java.util.UUID moduleVersionId, java.util.UUID lessonVersionId) {
        return moduleVersionId != null && moduleVersionId.equals(lessonVersionId) ? null : "That module belongs to a different programme version";
    }

    /** Why a learner's finish cannot be recorded, or null. */
    public static String finishProblem(String enrolmentStatus, LocalDate startDate, boolean sameVersion, String lessonStatus, boolean alreadyFinished,
                                       LocalDate on, LocalDate today) {
        if (EnrolmentRules.CANCELLED.equals(enrolmentStatus)) {
            return "This enrolment was cancelled";
        }
        if (!sameVersion) {
            return "That lesson belongs to a different programme version";
        }
        if (!AssessmentRules.APPROVED.equals(lessonStatus)) {
            return "Only an approved lesson can be marked as finished";
        }
        if (alreadyFinished) {
            return "This lesson is already marked as finished for this enrolment";
        }
        if (on == null) {
            return "Enter the day it was finished";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (startDate != null && on.isBefore(startDate)) {
            return "The day cannot be before the enrolment started";
        }
        return null;
    }

    public static String reverseProblem(String status, String reason) {
        if (REVERSED.equals(status)) {
            return "This record is already reversed";
        }
        return CohortRules.reasonProblem(reason);
    }

    /** A plain summary of progress. Never a grade. */
    public static String summary(int finished, int total) {
        if (total == 0) {
            return "No approved lessons for this version yet.";
        }
        return finished + " of " + total + (total == 1 ? " lesson" : " lessons") + " marked as finished. This is progress only. It is not an assessment outcome.";
    }
}

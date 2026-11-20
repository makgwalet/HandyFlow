package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.ApprovalThreshold;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenChoice;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Pure rules for written (circular) resolutions. The denominator is always ALL the voting members the resolution was sent to: nobody can be left out of the count by
 * not answering. UNANIMOUS is carried only if every member is for. MAJORITY_OF_ALL is carried only if MORE than half of all members are for, so a tie, silence and
 * abstentions all count against. Whether a committee may decide by written resolution, and by which threshold, is a matter for its charter: confirm before relying on it.
 */
public final class WrittenResolutionRules {

    private WrittenResolutionRules() {}

    public record Counts(int votesFor, int against, int abstained, int noResponse) {
        public int total() { return votesFor + against + abstained + noResponse; }
        public int answered() { return votesFor + against + abstained; }
    }

    /** null in the collection means the member has not answered. */
    public static Counts count(Collection<WrittenChoice> choices) {
        int f = 0, a = 0, ab = 0, n = 0;
        for (WrittenChoice c : choices) {
            if (c == null) n++;
            else switch (c) {
                case FOR -> f++;
                case AGAINST -> a++;
                case ABSTAIN -> ab++;
            }
        }
        return new Counts(f, a, ab, n);
    }

    public static WrittenStatus outcome(ApprovalThreshold threshold, Counts c) {
        int total = c.total();
        if (total == 0) return WrittenStatus.NOT_CARRIED;
        boolean carried = threshold == ApprovalThreshold.UNANIMOUS ? c.votesFor() == total : c.votesFor() * 2 > total;
        return carried ? WrittenStatus.CARRIED : WrittenStatus.NOT_CARRIED;
    }

    public static String describe(ApprovalThreshold threshold) {
        return threshold == ApprovalThreshold.UNANIMOUS
                ? "Unanimous: every voting member must be for. Anyone who is against, abstains or does not answer means it is not carried"
                : "Majority of all members: more than half of ALL voting members must be for. Silence, abstentions and a tie count against";
    }

    /** Why it cannot be closed yet, or null. Closable once everyone has answered, or once the deadline day has passed. */
    public static String closeProblem(Counts c, LocalDate deadline, LocalDate today) {
        if (c.noResponse() == 0 || today.isAfter(deadline)) return null;
        return c.noResponse() + " member(s) have not answered yet. It can be closed when everyone has answered or after the deadline (" + deadline + ")";
    }

    /** Reminders go to people who have not answered 2 days before the deadline and on the deadline day. */
    public static boolean remindToday(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, deadline);
        return days == 2 || days == 0;
    }

    public static String reminderText(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, deadline);
        return days <= 0 ? "closes today" : "closes in " + days + " days (" + deadline + ")";
    }
}

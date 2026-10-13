package za.co.handyflow.platform.debtcollection.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.PriorityBand;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Explainable, rule-based priority (0-100). Deliberately NOT a "likelihood to
 * pay" model: every point is traceable to a stated reason that the collector
 * sees next to the item, and a supervisor can override with an audited reason.
 * Rules: work type, deadline proximity, balance, case age, dispute status.
 */
@Component
public class WorkItemPriorityCalculator {

    public record Result(int score, PriorityBand band, List<String> reasons) {
        public String reasonText() { return String.join("; ", reasons); }
    }

    public Result score(WorkItem item, DebtCollectionCase c, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        int score = item.getType().baseScore();
        reasons.add(item.getType().name() + " (+" + item.getType().baseScore() + ")");

        LocalDate due = item.getDueDate();
        if (due != null) {
            long overdue = ChronoUnit.DAYS.between(due, today);
            if (overdue > 0) {
                int pts = (int) Math.min(20, overdue * 2);
                score += pts;
                reasons.add(overdue + " day(s) past due (+" + pts + ")");
            } else if (overdue == 0) {
                score += 10;
                reasons.add("due today (+10)");
            } else if (-overdue <= 3) {
                score += 5;
                reasons.add("due within 3 days (+5)");
            }
        }

        BigDecimal bal = c.getTotalOutstanding() != null ? c.getTotalOutstanding() : BigDecimal.ZERO;
        int balPts = bal.compareTo(BigDecimal.valueOf(100_000)) >= 0 ? 20
                : bal.compareTo(BigDecimal.valueOf(50_000)) >= 0 ? 15
                : bal.compareTo(BigDecimal.valueOf(20_000)) >= 0 ? 10
                : bal.compareTo(BigDecimal.valueOf(5_000)) >= 0 ? 5 : 0;
        if (balPts > 0) {
            score += balPts;
            reasons.add("balance R" + bal.setScale(0, java.math.RoundingMode.HALF_UP) + " (+" + balPts + ")");
        }

        long ageDays = c.getOpenedDate() != null ? ChronoUnit.DAYS.between(c.getOpenedDate(), today) : 0;
        int agePts = ageDays > 90 ? 10 : ageDays > 60 ? 7 : ageDays > 30 ? 4 : 0;
        if (agePts > 0) {
            score += agePts;
            reasons.add("case open " + ageDays + " days (+" + agePts + ")");
        }

        if (c.getStatus() == CaseStatus.DISPUTED && item.getType() != WorkItemType.DISPUTE_REVIEW) {
            score -= 15;
            reasons.add("case is disputed — collection paused (-15)");
        }

        score = Math.max(0, Math.min(100, score));
        return new Result(score, PriorityBand.of(score), reasons);
    }
}

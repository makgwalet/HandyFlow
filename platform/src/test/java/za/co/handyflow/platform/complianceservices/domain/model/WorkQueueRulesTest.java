package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkQueueRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final UUID C = UUID.randomUUID();

    private static Instant daysAgo(int n) { return TODAY.minusDays(n).atStartOfDay().toInstant(ZoneOffset.UTC); }

    @Test @DisplayName("an overdue deadline is high and says how late it is")
    void overdue() {
        var i = WorkQueueRules.deadline(TODAY, C, "Acme", "CIPC-AR", TODAY.minusDays(5));
        assertThat(i.severity()).isEqualTo("HIGH");
        assertThat(i.detail()).isEqualTo("5 days overdue");
        assertThat(WorkQueueRules.deadline(TODAY, C, "Acme", "x", TODAY.minusDays(1)).detail()).isEqualTo("1 day overdue");
    }

    @Test @DisplayName("within three days is high, within two weeks is medium, later is not queue material")
    void soon() {
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY).detail()).isEqualTo("Due today");
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY.plusDays(1)).detail()).isEqualTo("Due tomorrow");
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY.plusDays(3)).severity()).isEqualTo("HIGH");
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY.plusDays(4)).severity()).isEqualTo("MEDIUM");
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY.plusDays(14)).severity()).isEqualTo("MEDIUM");
        assertThat(WorkQueueRules.deadline(TODAY, C, "A", "x", TODAY.plusDays(15))).isNull();
    }

    @Test @DisplayName("a document waiting for review becomes medium after a week")
    void reviewAge() {
        assertThat(WorkQueueRules.documentReview(TODAY, C, "A", "Tax Clearance", daysAgo(0)).severity()).isEqualTo("LOW");
        assertThat(WorkQueueRules.documentReview(TODAY, C, "A", "Tax Clearance", daysAgo(7)).severity()).isEqualTo("LOW");
        var old = WorkQueueRules.documentReview(TODAY, C, "A", "Tax Clearance", daysAgo(8));
        assertThat(old.severity()).isEqualTo("MEDIUM");
        assertThat(old.detail()).isEqualTo("Waiting for review for 8 days");
    }

    @Test @DisplayName("an expired document is high, one expiring within a month is medium, later is ignored")
    void expiry() {
        assertThat(WorkQueueRules.documentExpiry(TODAY, C, "A", "BBBEE", TODAY.minusDays(1)).severity()).isEqualTo("HIGH");
        assertThat(WorkQueueRules.documentExpiry(TODAY, C, "A", "BBBEE", TODAY).detail()).isEqualTo("Expires today");
        assertThat(WorkQueueRules.documentExpiry(TODAY, C, "A", "BBBEE", TODAY.plusDays(30)).severity()).isEqualTo("MEDIUM");
        assertThat(WorkQueueRules.documentExpiry(TODAY, C, "A", "BBBEE", TODAY.plusDays(31))).isNull();
    }

    @Test @DisplayName("approvals and catalogue reviews are for admins only")
    void adminOnly() {
        var list = List.of(WorkQueueRules.approval(C, "Acme", "POPIA-1"), WorkQueueRules.catalogueReview(TODAY, "VAT-201", 2, daysAgo(10)),
                WorkQueueRules.documentReview(TODAY, C, "Acme", "Doc", daysAgo(1)));
        assertThat(WorkQueueRules.visibleTo(list, false)).hasSize(1);
        assertThat(WorkQueueRules.visibleTo(list, true)).hasSize(3);
        assertThat(WorkQueueRules.catalogueReview(TODAY, "VAT-201", 2, daysAgo(10)).severity()).isEqualTo("MEDIUM");
        assertThat(WorkQueueRules.catalogueReview(TODAY, "VAT-201", 2, null).clientId()).isNull();
    }

    @Test @DisplayName("an assessment left open for over two weeks is medium")
    void staleAssessment() {
        assertThat(WorkQueueRules.assessmentOpen(TODAY, C, "A", daysAgo(14)).severity()).isEqualTo("LOW");
        assertThat(WorkQueueRules.assessmentOpen(TODAY, C, "A", daysAgo(15)).severity()).isEqualTo("MEDIUM");
    }

    @Test @DisplayName("the queue puts the most urgent first, then the earliest date, then name, and drops nulls")
    void ordering() {
        var low = WorkQueueRules.documentReview(TODAY, C, "Zed", "Doc", daysAgo(1));
        var med = WorkQueueRules.deadline(TODAY, C, "Acme", "B", TODAY.plusDays(10));
        var highLate = WorkQueueRules.deadline(TODAY, C, "Acme", "A", TODAY.minusDays(1));
        var highEarly = WorkQueueRules.deadline(TODAY, C, "Beta", "A", TODAY.minusDays(9));
        var out = WorkQueueRules.order(java.util.Arrays.asList(low, med, null, highLate, highEarly));
        assertThat(out).hasSize(4);
        assertThat(out.get(0)).isEqualTo(highEarly);
        assertThat(out.get(1)).isEqualTo(highLate);
        assertThat(out.get(2)).isEqualTo(med);
        assertThat(out.get(3)).isEqualTo(low);
    }

    @Test @DisplayName("a case is urgent when overdue or due in three days, medium within two weeks, and low otherwise")
    void caseUrgency() {
        assertThat(WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "IN_PROGRESS", "HIGH", TODAY.minusDays(1)).severity()).isEqualTo("HIGH");
        assertThat(WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "IN_PROGRESS", "HIGH", TODAY.minusDays(1)).detail()).contains("overdue");
        assertThat(WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "OPEN", "LOW", TODAY.plusDays(3)).severity()).isEqualTo("HIGH");
        assertThat(WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "OPEN", "LOW", TODAY.plusDays(10)).severity()).isEqualTo("MEDIUM");
        assertThat(WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "OPEN", "LOW", TODAY.plusDays(40)).severity()).isEqualTo("LOW");
        var none = WorkQueueRules.caseItem(TODAY, C, "Acme", "T", "AWAITING_CLIENT", "MEDIUM", null);
        assertThat(none.severity()).isEqualTo("LOW");
        assertThat(none.detail()).isEqualTo("awaiting client · medium");
        assertThat(none.tab()).isEqualTo("cases");
    }
}

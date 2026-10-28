package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterCvAccessRules.Row;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterCvAccessRules.View;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterCvAccessRulesTest {
    private static final Instant T = Instant.parse("2026-10-28T08:10:00Z");

    @Test
    void daysAreLimited() {
        assertThat(RecruiterCvAccessRules.daysProblem(null)).isNull();
        assertThat(RecruiterCvAccessRules.daysProblem(1)).isNull();
        assertThat(RecruiterCvAccessRules.daysProblem(365)).isNull();
        assertThat(RecruiterCvAccessRules.daysProblem(0)).contains("between");
        assertThat(RecruiterCvAccessRules.daysProblem(366)).contains("between");
        assertThat(RecruiterCvAccessRules.daysOrDefault(null)).isEqualTo(30);
    }

    @Test
    void aFewViewsAreNotFlagged() {
        List<Row> rows = RecruiterCvAccessRules.summarise(List.of(
                new View("u1", "Thandi", UUID.randomUUID(), T), new View("u1", "Thandi", UUID.randomUUID(), T.plusSeconds(60))));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).views()).isEqualTo(2);
        assertThat(rows.get(0).candidates()).isEqualTo(2);
        assertThat(rows.get(0).flagged()).isFalse();
    }

    @Test
    void manyViewsInAnHourAreFlaggedAndCountedPerPerson() {
        List<View> views = new ArrayList<>();
        for (int i = 0; i < 30; i++) views.add(new View("bulk", "Sipho", UUID.randomUUID(), T.plusSeconds(i * 60L)));
        for (int i = 0; i < 29; i++) views.add(new View("slow", "Lerato", UUID.randomUUID(), T.plusSeconds(i * 60L)));
        // the same count spread over two hours is not a burst
        for (int i = 0; i < 20; i++) views.add(new View("spread", "Naledi", UUID.randomUUID(), T.plusSeconds(i * 60L)));
        for (int i = 0; i < 20; i++) views.add(new View("spread", "Naledi", UUID.randomUUID(), T.plusSeconds(7200 + i * 60L)));
        List<Row> rows = RecruiterCvAccessRules.summarise(views);
        assertThat(rows).extracting(Row::actorName).containsExactly("Naledi", "Sipho", "Lerato");
        assertThat(rows.get(1).flagged()).isTrue();
        assertThat(rows.get(1).busiestHourViews()).isEqualTo(30);
        assertThat(rows.get(2).flagged()).isFalse();
        assertThat(rows.get(0).views()).isEqualTo(40);
        assertThat(rows.get(0).flagged()).isFalse();
        assertThat(rows.get(0).busiestHourViews()).isEqualTo(20);
    }

    @Test
    void repeatViewsOfOneCandidateCountOnceAsACandidate() {
        UUID app = UUID.randomUUID();
        List<Row> rows = RecruiterCvAccessRules.summarise(List.of(new View("u", null, app, T), new View("u", null, app, T.plusSeconds(5))));
        assertThat(rows.get(0).views()).isEqualTo(2);
        assertThat(rows.get(0).candidates()).isEqualTo(1);
        assertThat(rows.get(0).actorName()).isEqualTo("Unknown");
    }
}

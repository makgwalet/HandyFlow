package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.ForeignIdentityRules.Expiring;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignIdentityRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void aDocumentIsMentionedOnTheWarningDaysOnly() {
        for (int days : new int[]{90, 60, 30, 14, 7, 1, 0}) assertThat(ForeignIdentityRules.remindToday(TODAY.plusDays(days), TODAY)).isTrue();
        for (int days : new int[]{91, 89, 45, 31, 29, 15, 13, 8, 6, 5, 2}) assertThat(ForeignIdentityRules.remindToday(TODAY.plusDays(days), TODAY)).isFalse();
    }

    @Test
    void anExpiredDocumentIsMentionedTheNextDayThenWeeklyForEightWeeks() {
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(2), TODAY)).isFalse();
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(7), TODAY)).isTrue();
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(14), TODAY)).isTrue();
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(56), TODAY)).isTrue();
        assertThat(ForeignIdentityRules.remindToday(TODAY.minusDays(63), TODAY)).isFalse();
    }

    @Test
    void noDateMeansNoReminder() {
        assertThat(ForeignIdentityRules.remindToday(null, TODAY)).isFalse();
    }

    @Test
    void theWordingSaysHowLongIsLeft() {
        assertThat(ForeignIdentityRules.when(TODAY.plusDays(30), TODAY)).isEqualTo("expires in 30 days");
        assertThat(ForeignIdentityRules.when(TODAY.plusDays(1), TODAY)).isEqualTo("expires tomorrow");
        assertThat(ForeignIdentityRules.when(TODAY, TODAY)).isEqualTo("expires today");
        assertThat(ForeignIdentityRules.when(TODAY.minusDays(1), TODAY)).isEqualTo("expired 1 day ago");
        assertThat(ForeignIdentityRules.when(TODAY.minusDays(8), TODAY)).isEqualTo("expired 8 days ago");
    }

    @Test
    void theEmployeeIsToldWhatToDoAndNeverTheDocumentNumber() {
        String m = ForeignIdentityRules.employeeMessage(ForeignIdentityRules.PERMIT, TODAY.plusDays(14), TODAY);
        assertThat(m).contains("work permit expires in 14 days").contains("renewed permit");
        assertThat(ForeignIdentityRules.employeeTitle(ForeignIdentityRules.PERMIT, TODAY.plusDays(14), TODAY)).isEqualTo("Your work permit is about to expire");
        assertThat(ForeignIdentityRules.employeeTitle(ForeignIdentityRules.PASSPORT, TODAY.minusDays(1), TODAY)).isEqualTo("Your passport has expired");
        assertThat(ForeignIdentityRules.employeeMessage(ForeignIdentityRules.PASSPORT, TODAY.plusDays(30), TODAY)).contains("renew it");
    }

    @Test
    void hrGetsOneListSoonestFirstAndAWarningWhenAPermitHasLapsed() {
        List<Expiring> items = List.of(new Expiring("Tendai Moyo", ForeignIdentityRules.PERMIT, TODAY.plusDays(30)),
                new Expiring("Grace Banda", ForeignIdentityRules.PASSPORT, TODAY.plusDays(7)));
        String m = ForeignIdentityRules.hrMessage(items, TODAY);
        assertThat(m).startsWith("Grace Banda: passport expires in 7 days; Tendai Moyo: work permit expires in 30 days.");
        assertThat(m).doesNotContain("may not lawfully");
        String lapsed = ForeignIdentityRules.hrMessage(List.of(new Expiring("Tendai Moyo", ForeignIdentityRules.PERMIT, TODAY.minusDays(1))), TODAY);
        assertThat(lapsed).contains("work permit expired 1 day ago").contains("may not lawfully keep working");
        String lapsedPassport = ForeignIdentityRules.hrMessage(List.of(new Expiring("Grace Banda", ForeignIdentityRules.PASSPORT, TODAY.minusDays(1))), TODAY);
        assertThat(lapsedPassport).doesNotContain("may not lawfully");
        assertThat(ForeignIdentityRules.hrTitle(2)).isEqualTo("Work permits and passports needing attention (2)");
    }

    @Test
    void aLongListIsCutAndSaysHowManyMore() {
        List<Expiring> many = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) many.add(new Expiring("P" + i, ForeignIdentityRules.PERMIT, TODAY.plusDays(30)));
        assertThat(ForeignIdentityRules.hrMessage(many, TODAY)).contains("and 3 more");
    }

    @Test
    void theFilterTakesZeroToAYearAndAFewDays() {
        assertThat(ForeignIdentityRules.filterProblem(null)).isNotNull();
        assertThat(ForeignIdentityRules.filterProblem(-1)).contains("between 0 and 366");
        assertThat(ForeignIdentityRules.filterProblem(367)).contains("between 0 and 366");
        for (int d : new int[]{0, 30, 90, 366}) assertThat(ForeignIdentityRules.filterProblem(d)).isNull();
    }

    @Test
    void theFilterLimitIsTodayPlusTheDays() {
        assertThat(ForeignIdentityRules.filterLimit(TODAY, 0)).isEqualTo(TODAY);
        assertThat(ForeignIdentityRules.filterLimit(TODAY, 90)).isEqualTo(LocalDate.of(2027, 1, 26));
    }
}

package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CareerRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate JOINED = LocalDate.of(2024, 3, 1);

    @Test
    void onlyWhatChangedIsReported() {
        List<CareerRules.Change> c = CareerRules.changes("Clerk", "Senior Clerk", "Finance", "Finance", "PERMANENT", "PERMANENT");
        assertEquals(1, c.size());
        assertEquals(CareerRules.TITLE_CHANGED, c.get(0).kind());
        assertEquals("Clerk", c.get(0).from());
        assertEquals("Senior Clerk", c.get(0).to());
    }

    @Test
    void blankAndNullAreTheSame() {
        assertTrue(CareerRules.changes(null, " ", "", null, "FIXED_TERM", "FIXED_TERM").isEmpty());
        assertTrue(CareerRules.changes(" Clerk ", "Clerk", null, null, null, null).isEmpty());
    }

    @Test
    void severalChangesComeInOrder() {
        List<CareerRules.Change> c = CareerRules.changes("Clerk", "Manager", "Finance", "Operations", "FIXED_TERM", "PERMANENT");
        assertEquals(List.of(CareerRules.TITLE_CHANGED, CareerRules.DEPARTMENT_CHANGED, CareerRules.TYPE_CHANGED), c.stream().map(CareerRules.Change::kind).toList());
    }

    @Test
    void gainingATitleFromNothingIsAChange() {
        List<CareerRules.Change> c = CareerRules.changes(null, "Clerk", null, null, null, null);
        assertEquals(1, c.size());
        assertNull(c.get(0).from());
    }

    @Test
    void anEarlierJobNeedsEmployerTitleAndDates() {
        LocalDate from = LocalDate.of(2018, 1, 1), to = LocalDate.of(2023, 12, 31);
        assertNull(CareerRules.manualProblem("PRIOR_EMPLOYMENT", from, to, "Acme", "Clerk", null, null, JOINED, TODAY));
        assertTrue(CareerRules.manualProblem("PRIOR_EMPLOYMENT", from, to, " ", "Clerk", null, null, JOINED, TODAY).contains("employer"));
        assertTrue(CareerRules.manualProblem("PRIOR_EMPLOYMENT", from, to, "Acme", null, null, null, JOINED, TODAY).contains("job title"));
        assertTrue(CareerRules.manualProblem("PRIOR_EMPLOYMENT", from, null, "Acme", "Clerk", null, null, JOINED, TODAY).contains("left"));
        assertTrue(CareerRules.manualProblem("PRIOR_EMPLOYMENT", to, from, "Acme", "Clerk", null, null, JOINED, TODAY).contains("before starting"));
    }

    @Test
    void anEarlierJobCannotRunPastTheDayTheyJoinedUs() {
        assertNotNull(CareerRules.manualProblem("PRIOR_EMPLOYMENT", LocalDate.of(2022, 1, 1), JOINED.plusDays(1), "Acme", "Clerk", null, null, JOINED, TODAY));
        assertNull(CareerRules.manualProblem("PRIOR_EMPLOYMENT", LocalDate.of(2022, 1, 1), JOINED, "Acme", "Clerk", null, null, JOINED, TODAY));
    }

    @Test
    void aPromotionNeedsTheNewTitleAndAfterTheStartDate() {
        LocalDate d = LocalDate.of(2025, 6, 1);
        assertNull(CareerRules.manualProblem("PROMOTION", d, null, null, "Senior Clerk", null, null, JOINED, TODAY));
        assertTrue(CareerRules.manualProblem("PROMOTION", d, null, null, null, null, null, JOINED, TODAY).contains("promoted to"));
        assertTrue(CareerRules.manualProblem("PROMOTION", JOINED.minusDays(1), null, null, "Senior Clerk", null, null, JOINED, TODAY).contains("before the person joined"));
        assertNotNull(CareerRules.manualProblem("PROMOTION", d, d.plusDays(1), null, "Senior Clerk", null, null, JOINED, TODAY));
    }

    @Test
    void aTransferNeedsADepartmentAndOtherNeedsANote() {
        LocalDate d = LocalDate.of(2025, 6, 1);
        assertNull(CareerRules.manualProblem("TRANSFER", d, null, null, null, "Operations", null, JOINED, TODAY));
        assertNotNull(CareerRules.manualProblem("TRANSFER", d, null, null, null, null, null, JOINED, TODAY));
        assertNull(CareerRules.manualProblem("OTHER", d, null, null, null, null, "Acting manager for 3 months", JOINED, TODAY));
        assertNotNull(CareerRules.manualProblem("OTHER", d, null, null, null, null, " ", JOINED, TODAY));
    }

    @Test
    void theSystemKindsCannotBeAddedByHand() {
        assertNotNull(CareerRules.manualProblem("SALARY_CHANGED", TODAY, null, null, null, null, "x", JOINED, TODAY));
        assertNotNull(CareerRules.manualProblem("HIRED", TODAY, null, null, null, null, "x", JOINED, TODAY));
        assertNotNull(CareerRules.manualProblem(null, TODAY, null, null, null, null, "x", JOINED, TODAY));
    }

    @Test
    void aDateMoreThanAYearAheadIsRefused() {
        assertNotNull(CareerRules.manualProblem("PROMOTION", TODAY.plusYears(1).plusDays(1), null, null, "Manager", null, null, JOINED, TODAY));
        assertNull(CareerRules.manualProblem("PROMOTION", TODAY.plusYears(1), null, null, "Manager", null, null, JOINED, TODAY));
    }

    @Test
    void percentChangeIsToOneDecimal() {
        assertEquals(new BigDecimal("8.3"), CareerRules.percent("30000", "32500"));
        assertEquals(new BigDecimal("-5.0"), CareerRules.percent("40000", "38000"));
        assertNull(CareerRules.percent("0", "100"));
        assertNull(CareerRules.percent(null, "100"));
        assertNull(CareerRules.percent("abc", "100"));
    }

    @Test
    void labelsAreReadable() {
        assertEquals("Joined", CareerRules.label("HIRED"));
        assertEquals("Earlier employer", CareerRules.label("PRIOR_EMPLOYMENT"));
        assertEquals("UNKNOWN", CareerRules.label("UNKNOWN"));
    }
}

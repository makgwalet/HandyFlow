package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static za.co.handyflow.platform.hr.domain.model.EmployeeChangeRules.*;

class EmployeeChangeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private final UUID asker = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void aSensibleCompensationPasses() {
        assertThat(compensationProblem(d("30000"), d("1000"), d("1500"), d("2000"), "MONTHLY")).isNull();
        assertThat(compensationProblem(d("30000"), null, null, null, null)).isNull();
    }

    @Test
    void nonsenseCompensationIsRefused() {
        assertThat(compensationProblem(d("0"), null, null, null, null)).contains("more than zero");
        assertThat(compensationProblem(null, null, null, null, null)).contains("more than zero");
        assertThat(compensationProblem(d("6000000"), null, null, null, null)).contains("too high");
        assertThat(compensationProblem(d("30000"), d("-1"), null, null, null)).contains("negative");
        assertThat(compensationProblem(d("30000"), null, d("20000"), d("15000"), null)).contains("together are more than");
        assertThat(compensationProblem(d("30000"), d("40000"), null, null, null)).contains("travel allowance");
        assertThat(compensationProblem(d("30000"), null, null, null, "DAILY")).contains("Pay frequency");
    }

    @Test
    void theEffectiveDateHasLimits() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        assertThat(effectiveProblem(TODAY, start, TODAY)).isNull();
        assertThat(effectiveProblem(TODAY.minusDays(31), start, TODAY)).isNull();
        assertThat(effectiveProblem(TODAY.minusDays(32), start, TODAY)).contains("more than a month ago");
        assertThat(effectiveProblem(TODAY.plusDays(366), start, TODAY)).isNull();
        assertThat(effectiveProblem(TODAY.plusDays(367), start, TODAY)).contains("more than a year");
        assertThat(effectiveProblem(LocalDate.of(2024, 12, 31), start, LocalDate.of(2025, 1, 5))).contains("before the employee started");
        assertThat(effectiveProblem(null, start, TODAY)).contains("Choose");
    }

    @Test
    void aChangeNeedsAReason() {
        assertThat(reasonProblem(" ")).contains("reason");
        assertThat(reasonProblem("Annual increase")).isNull();
        assertThat(reasonProblem("x".repeat(501))).contains("500");
    }

    @Test
    void findsTheFieldsThatReallyChanged() {
        Map<String, String> before = new LinkedHashMap<>(Map.of(GROSS, "30000.00", TRAVEL, "0", PENSION, "1500"));
        Map<String, String> after = new LinkedHashMap<>();
        after.put(GROSS, "30000");        // same number, written differently
        after.put(TRAVEL, "500");
        after.put(PENSION, "1500.00");
        after.put(MEDICAL, "800");        // was not set before
        assertThat(changedFields(before, after)).isEqualTo(List.of(TRAVEL, MEDICAL));
    }

    @Test
    void textFieldsAreComparedAsText() {
        Map<String, String> before = Map.of(BANK_NAME, "FNB", ACCOUNT, "62012345678");
        Map<String, String> after = new LinkedHashMap<>();
        after.put(BANK_NAME, "FNB");
        after.put(ACCOUNT, "62012345679");
        assertThat(changedFields(before, after)).isEqualTo(List.of(ACCOUNT));
    }

    @Test
    void anotherPersonDecidesAndTheAskerCannot() {
        assertThat(checkCanDecide(PENDING, asker, other, false)).isFalse();
        assertThrows(AccessDeniedException.class, () -> checkCanDecide(PENDING, asker, asker, false));
        assertThat(checkCanDecide(PENDING, asker, asker, true)).isTrue();
        assertThrows(AccessDeniedException.class, () -> checkCanDecide(PENDING, asker, null, true));
    }

    @Test
    void onlyAWaitingChangeCanBeDecided() {
        for (String s : new String[] {APPROVED, APPLIED, REJECTED, CANCELLED})
            assertThrows(IllegalStateException.class, () -> checkCanDecide(s, asker, other, false));
    }

    @Test
    void rejectingNeedsAReason() {
        checkCanReject(PENDING, "Salary does not match the offer");
        assertThrows(IllegalArgumentException.class, () -> checkCanReject(PENDING, " "));
        assertThrows(IllegalStateException.class, () -> checkCanReject(APPLIED, "Too late"));
    }

    @Test
    void onlyTheAskerCancels() {
        checkCanCancel(PENDING, asker, asker);
        assertThrows(AccessDeniedException.class, () -> checkCanCancel(PENDING, asker, other));
        assertThrows(IllegalStateException.class, () -> checkCanCancel(APPROVED, asker, asker));
    }

    @Test
    void anApprovedChangeIsAppliedOnceItsDateHasCome() {
        assertThat(isDue(APPROVED, TODAY, TODAY)).isTrue();
        assertThat(isDue(APPROVED, TODAY.minusDays(3), TODAY)).isTrue();
        assertThat(isDue(APPROVED, TODAY.plusDays(1), TODAY)).isFalse();
        assertThat(isDue(PENDING, TODAY, TODAY)).isFalse();
        assertThat(isDue(APPLIED, TODAY, TODAY)).isFalse();
    }

    @Test
    void fieldsHaveReadableLabels() {
        assertThat(label(GROSS)).isEqualTo("Gross salary");
        assertThat(label(ACCOUNT)).isEqualTo("Account number");
        assertThat(label("other")).isEqualTo("other");
    }
}

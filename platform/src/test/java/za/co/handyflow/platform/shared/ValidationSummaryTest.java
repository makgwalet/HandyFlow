package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationSummaryTest {

    @Test
    void oneProblemIsShownAsIs() {
        assertThat(GlobalExceptionHandler.validationSummary(Map.of("planYear", "The plan year must be 2020 or later.")))
                .isEqualTo("The plan year must be 2020 or later.");
    }

    @Test
    void severalProblemsShowTheFirstAndHowManyMore() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", "must not be blank");
        m.put("clientId", "must not be null");
        m.put("notes", "too long");
        assertThat(GlobalExceptionHandler.validationSummary(m)).isEqualTo("must not be blank (and 2 more)");
    }

    @Test
    void noDetailFallsBackToTheOldWords() {
        assertThat(GlobalExceptionHandler.validationSummary(Map.of())).isEqualTo("Validation failed");
    }
}

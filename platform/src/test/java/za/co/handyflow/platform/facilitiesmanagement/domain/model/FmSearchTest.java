package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FmSearchTest {

    @Test
    void blankMatchesEverything() {
        assertThat(FmSearch.likePattern(null)).isEqualTo("%");
        assertThat(FmSearch.likePattern("   ")).isEqualTo("%");
    }

    @Test
    void wrapsInLowerCaseContains() {
        assertThat(FmSearch.likePattern("  Acme Corp ")).isEqualTo("%acme corp%");
    }

    @Test
    void wildcardsAreTakenLiterally() {
        assertThat(FmSearch.likePattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }

    @Test
    void veryLongInputIsCut() {
        assertThat(FmSearch.likePattern("x".repeat(500)).length()).isEqualTo(FmSearch.MAX_LENGTH + 2);
    }
}

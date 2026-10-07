package za.co.handyflow.platform.clinic.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SaIdNumberTest {

    @Test
    @DisplayName("a well-formed ID yields date of birth, sex digits and citizenship")
    void validId() {
        SaIdNumber id = SaIdNumber.parseOrNull("8001015009087");

        assertThat(id).isNotNull();
        assertThat(id.dateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(id.male()).isTrue();      // sequence 5009
        assertThat(id.citizen()).isTrue();   // digit 0
    }

    @Test
    @DisplayName("a two-digit year in the future is read as 19xx")
    void futureYearIsPreviousCentury() {
        assertThat(SaIdNumber.parseOrNull("6405037113086").dateOfBirth()).isEqualTo(LocalDate.of(1964, 5, 3));
    }

    @Test
    @DisplayName("wrong check digit, impossible date and bad citizenship digit are all rejected with a reason")
    void invalidIds() {
        assertThat(SaIdNumber.problem("8001015009088")).contains("check digit");
        assertThat(SaIdNumber.problem("8013015009087")).contains("date of birth");
        assertThat(SaIdNumber.problem("8001015009287")).contains("citizenship");
        assertThat(SaIdNumber.parseOrNull("8001015009088")).isNull();
    }

    @Test
    @DisplayName("anything that is not 13 digits is another document, not an SA ID")
    void otherDocuments() {
        assertThat(SaIdNumber.looksLikeSaId("A1234567")).isFalse();
        assertThat(SaIdNumber.looksLikeSaId("800101500908")).isFalse();
        assertThat(SaIdNumber.looksLikeSaId(null)).isFalse();
        assertThat(SaIdNumber.looksLikeSaId(" 8001015009087 ")).isTrue();
    }

    @Test
    @DisplayName("date of birth agreement ignores the century but not the day")
    void dobAgreement() {
        SaIdNumber id = SaIdNumber.parseOrNull("8001015009087");
        assertThat(id.matchesDateOfBirth(LocalDate.of(1980, 1, 1))).isTrue();
        assertThat(id.matchesDateOfBirth(LocalDate.of(1980, 1, 2))).isFalse();
        assertThat(id.matchesDateOfBirth(null)).isFalse();
    }
}

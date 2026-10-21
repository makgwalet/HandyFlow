package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.MedicalSchemeRules.Cover;
import za.co.handyflow.platform.hr.domain.model.MedicalSchemeRules.Members;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicalSchemeRulesTest {

    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test
    void membershipOnRecordGivesAnExactCount() {
        var covers = List.of(new Cover(d("2026-03-01"), null, 2));
        assertThat(MedicalSchemeRules.membersOn(covers, d("2026-10-31"), BigDecimal.ZERO)).isEqualTo(new Members(3, false));
    }

    @Test
    void theRightMembershipIsPickedForThePeriod() {
        var covers = List.of(new Cover(d("2025-03-01"), d("2026-02-28"), 0), new Cover(d("2026-03-01"), null, 3));
        assertThat(MedicalSchemeRules.membersOn(covers, d("2026-02-28"), null).count()).isEqualTo(1);
        assertThat(MedicalSchemeRules.membersOn(covers, d("2026-03-01"), null).count()).isEqualTo(4);
    }

    @Test
    void withoutARecordAContributionMeansTheEmployeeOnlyAndIsFlagged() {
        assertThat(MedicalSchemeRules.membersOn(List.of(), d("2026-10-31"), new BigDecimal("1200.00"))).isEqualTo(new Members(1, true));
    }

    @Test
    void withoutARecordAndWithoutAContributionThereIsNoCredit() {
        assertThat(MedicalSchemeRules.membersOn(List.of(), d("2026-10-31"), BigDecimal.ZERO)).isEqualTo(new Members(0, false));
        assertThat(MedicalSchemeRules.membersOn(List.of(), d("2026-10-31"), null)).isEqualTo(new Members(0, false));
    }

    @Test
    void aMembershipThatEndedDoesNotCoverLaterDays() {
        var covers = List.of(new Cover(d("2025-03-01"), d("2025-12-31"), 1));
        assertThat(MedicalSchemeRules.membersOn(covers, d("2026-01-01"), BigDecimal.ZERO).count()).isEqualTo(0);
    }

    @Test
    void aNewMembershipClosesTheOpenOne() {
        var existing = List.of(new Cover(d("2026-03-01"), null, 1));
        assertThat(MedicalSchemeRules.checkNew(existing, d("2026-09-01"), 2)).isEqualTo(d("2026-03-01"));
    }

    @Test
    void theFirstMembershipClosesNothing() {
        assertThat(MedicalSchemeRules.checkNew(List.of(), d("2026-03-01"), 0)).isNull();
    }

    @Test
    void refusesOverlapsAndBackdatingBeforeTheLatestStart() {
        var closed = List.of(new Cover(d("2026-03-01"), d("2026-08-31"), 1));
        assertThatThrownBy(() -> MedicalSchemeRules.checkNew(closed, d("2026-08-31"), 1)).hasMessageContaining("overlaps");
        assertThatThrownBy(() -> MedicalSchemeRules.checkNew(closed, d("2026-03-01"), 1)).hasMessageContaining("must start after");
        assertThat(MedicalSchemeRules.checkNew(closed, d("2026-09-01"), 1)).isNull();
    }

    @Test
    void refusesBadDependantsAndMissingDate() {
        assertThatThrownBy(() -> MedicalSchemeRules.checkNew(List.of(), d("2026-03-01"), -1)).hasMessageContaining("0 to 20");
        assertThatThrownBy(() -> MedicalSchemeRules.checkNew(List.of(), d("2026-03-01"), 21)).hasMessageContaining("0 to 20");
        assertThatThrownBy(() -> MedicalSchemeRules.checkNew(List.of(), null, 1)).hasMessageContaining("start date");
    }
}

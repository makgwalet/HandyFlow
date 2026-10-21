package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ErRulesTest {

    @Test
    void aCategoryMustBelongToItsType() {
        assertThat(ErRules.categoryProblem("DISCIPLINARY", "THEFT")).isNull();
        assertThat(ErRules.categoryProblem("GRIEVANCE", "THEFT")).isNotNull();
        assertThat(ErRules.categoryProblem("INCAPACITY", "ILL_HEALTH")).isNull();
        assertThat(ErRules.categoryProblem("OTHER", "OTHER")).isNotNull();
        assertThat(ErRules.categoryProblem(null, null)).isNotNull();
    }

    @Test
    void medicalAndHarassmentCasesAreConfidentialByDefault() {
        assertThat(ErRules.confidentialByDefault("INCAPACITY", "ILL_HEALTH")).isTrue();
        assertThat(ErRules.confidentialByDefault("INCAPACITY", "POOR_PERFORMANCE")).isFalse();
        assertThat(ErRules.confidentialByDefault("GRIEVANCE", "HARASSMENT")).isTrue();
        assertThat(ErRules.confidentialByDefault("GRIEVANCE", "DISCRIMINATION")).isTrue();
        assertThat(ErRules.confidentialByDefault("GRIEVANCE", "PAY_AND_BENEFITS")).isFalse();
        assertThat(ErRules.confidentialByDefault("DISCIPLINARY", "HARASSMENT")).isTrue();
        assertThat(ErRules.confidentialByDefault("DISCIPLINARY", "LATENESS")).isFalse();
    }

    @Test
    void aDisciplinaryCaseMovesThroughTheFullProcess() {
        assertThat(ErRules.canMove("DISCIPLINARY", "REPORTED", "INVESTIGATING")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "INVESTIGATING", "NOTICE_ISSUED")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "NOTICE_ISSUED", "HEARING_HELD")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "HEARING_HELD", "DECIDED")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "DECIDED", "APPEALED")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "APPEALED", "CLOSED")).isTrue();
    }

    @Test
    void aDisciplinaryCaseCannotSkipTheHearing() {
        assertThat(ErRules.canMove("DISCIPLINARY", "INVESTIGATING", "HEARING_HELD")).isFalse();
        assertThat(ErRules.canMove("DISCIPLINARY", "NOTICE_ISSUED", "DECIDED")).isFalse();
        assertThat(ErRules.canMove("DISCIPLINARY", "REPORTED", "APPEALED")).isFalse();
    }

    @Test
    void theInformalRouteIsOnlyForDiscipline() {
        assertThat(ErRules.canMove("DISCIPLINARY", "REPORTED", "DECIDED")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "INVESTIGATING", "DECIDED")).isTrue();
        assertThat(ErRules.canMove("GRIEVANCE", "REPORTED", "DECIDED")).isFalse();
        assertThat(ErRules.canMove("INCAPACITY", "REPORTED", "DECIDED")).isFalse();
    }

    @Test
    void aGrievanceHasNoHearingStage() {
        assertThat(ErRules.canMove("GRIEVANCE", "INVESTIGATING", "DECIDED")).isTrue();
        assertThat(ErRules.canMove("GRIEVANCE", "INVESTIGATING", "NOTICE_ISSUED")).isFalse();
        assertThat(ErRules.canMove("GRIEVANCE", "NOTICE_ISSUED", "HEARING_HELD")).isFalse();
    }

    @Test
    void incapacityGoesThroughAMeetingAndCanReturnToInvestigation() {
        assertThat(ErRules.canMove("INCAPACITY", "INVESTIGATING", "NOTICE_ISSUED")).isTrue();
        assertThat(ErRules.canMove("INCAPACITY", "HEARING_HELD", "INVESTIGATING")).isTrue();
        assertThat(ErRules.canMove("INCAPACITY", "HEARING_HELD", "DECIDED")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "HEARING_HELD", "INVESTIGATING")).isFalse();
    }

    @Test
    void aCaseCanBeWithdrawnBeforeADecisionButNotAfter() {
        assertThat(ErRules.canMove("DISCIPLINARY", "NOTICE_ISSUED", "WITHDRAWN")).isTrue();
        assertThat(ErRules.canMove("GRIEVANCE", "REPORTED", "WITHDRAWN")).isTrue();
        assertThat(ErRules.canMove("DISCIPLINARY", "DECIDED", "WITHDRAWN")).isFalse();
        assertThat(ErRules.canMove("DISCIPLINARY", "CLOSED", "WITHDRAWN")).isFalse();
    }

    @Test
    void aClosedOrWithdrawnCaseCannotMoveAnywhere() {
        for (String to : new String[]{"REPORTED", "INVESTIGATING", "DECIDED", "CLOSED", "APPEALED"}) {
            assertThat(ErRules.canMove("DISCIPLINARY", "CLOSED", to)).isFalse();
            assertThat(ErRules.canMove("DISCIPLINARY", "WITHDRAWN", to)).isFalse();
        }
    }

    @Test
    void seeingACaseNeedsAnEmployeeRelationsPermission() {
        UUID me = UUID.randomUUID();
        assertThat(ErRules.canSee(Set.of("HR_ER_READ"), me, false, null)).isTrue();
        assertThat(ErRules.canSee(Set.of("HR_ER_MANAGE"), me, false, null)).isTrue();
        assertThat(ErRules.canSee(Set.of("HR_READ", "HR_MANAGE"), me, false, null)).isFalse();
        assertThat(ErRules.canSee(Set.of(), me, false, null)).isFalse();
    }

    @Test
    void aConfidentialCaseNeedsSensitiveAccessOrOwnership() {
        UUID me = UUID.randomUUID();
        assertThat(ErRules.canSee(Set.of("HR_ER_READ"), me, true, UUID.randomUUID())).isFalse();
        assertThat(ErRules.canSee(Set.of("HR_ER_READ", "HR_SENSITIVE_READ"), me, true, UUID.randomUUID())).isTrue();
        assertThat(ErRules.canSee(Set.of("HR_ER_READ"), me, true, me)).isTrue();
        assertThat(ErRules.canSee(Set.of("HR_SENSITIVE_READ"), me, true, me)).isFalse();
    }

    @Test
    void workingDaysSkipTheWeekend() {
        LocalDate friday = LocalDate.of(2026, 10, 16);
        assertThat(ErRules.addWorkingDays(friday, 1)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(ErRules.addWorkingDays(friday, 5)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(ErRules.addWorkingDays(LocalDate.of(2026, 10, 14), 0)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void textMustBePresentAndNotTooLong() {
        assertThat(ErRules.textProblem("The reasons", null, 10)).contains("required");
        assertThat(ErRules.textProblem("The reasons", "  ", 10)).contains("required");
        assertThat(ErRules.textProblem("The reasons", "12345678901", 10)).contains("too long");
        assertThat(ErRules.textProblem("The reasons", "fine", 10)).isNull();
    }

    @Test
    void policyDefaultsAreUsableAndOrdered() {
        assertThat(ErPolicy.defaults().problem()).isNull();
        assertThat(ErPolicy.defaults().monthsFor("WRITTEN_WARNING")).isEqualTo(6);
        assertThat(ErPolicy.defaults().monthsFor("DISMISSAL")).isEqualTo(0);
        assertThat(new ErPolicy(48, 12, 6, 3, 7, 5, 28).problem()).contains("lower warning");
        assertThat(new ErPolicy(0, 3, 6, 12, 7, 5, 28).problem()).contains("Notice");
        assertThat(new ErPolicy(48, 3, 6, 12, 0, 5, 28).problem()).contains("appeal");
    }
}

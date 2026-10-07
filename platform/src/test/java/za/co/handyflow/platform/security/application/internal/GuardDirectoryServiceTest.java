package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** The guards list: what may reach the SQL, how rows sort, and how PSiRA state and last activity are decided. */
class GuardDirectoryServiceTest {

    private final GuardDirectoryService service = new GuardDirectoryService(mock(JdbcTemplate.class), mock(GuardRepository.class), mock(GuardService.class));
    private static final TenantId TENANT = TenantId.generate();

    private GuardDirectoryService.Query query(String status, String grade, String psira, String screening, String sort) {
        return new GuardDirectoryService.Query(null, status, grade, psira, screening, null, sort, false, 0, 25);
    }

    @Test
    void anUnknownFilterValueIsRefusedBeforeAnyQueryRuns() {
        LocalDate today = LocalDate.of(2026, 10, 7);
        assertThatThrownBy(() -> service.search(TENANT, query("RETIRED", null, null, null, "name"), today)).isInstanceOf(HandyFlowException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> service.search(TENANT, query(null, "Z", null, null, "name"), today)).isInstanceOf(HandyFlowException.class).hasMessageContaining("grade");
        assertThatThrownBy(() -> service.search(TENANT, query(null, null, "SOON", null, "name"), today)).isInstanceOf(HandyFlowException.class).hasMessageContaining("psira");
        assertThatThrownBy(() -> service.search(TENANT, query(null, null, null, "OK", "name"), today)).isInstanceOf(HandyFlowException.class).hasMessageContaining("screening");
    }

    @Test
    void sortKeysComeFromAFixedListAndEndOnNameAndIdSoPagesAreStable() {
        assertThat(GuardDirectoryService.orderBy("name", false)).isEqualTo("ln ASC, fn ASC, id");
        assertThat(GuardDirectoryService.orderBy("name", true)).isEqualTo("ln DESC, fn DESC, id");
        assertThat(GuardDirectoryService.orderBy("grade", true)).isEqualTo("grade_key DESC, ln ASC, fn ASC, id");
        assertThat(GuardDirectoryService.orderBy("psira", false)).startsWith("psira_key ASC NULLS LAST, ");
        assertThat(GuardDirectoryService.orderBy("activity", true)).startsWith("GREATEST(last_shift, last_scan) DESC NULLS LAST, ");
        // anything else, including an attempt at injection, falls back to sorting by name
        assertThat(GuardDirectoryService.orderBy("name; DROP TABLE security_guards", false)).isEqualTo("ln ASC, fn ASC, id");
        assertThat(GuardDirectoryService.orderBy(null, false)).isEqualTo("ln ASC, fn ASC, id");
    }

    @Test
    void psiraStateUsesThirtyDaysAsExpiringAndTreatsAMissingDateAsNone() {
        String sql = GuardDirectoryService.psiraStateSql(LocalDate.of(2026, 10, 7));
        assertThat(sql).contains("IS NULL THEN 'NONE'")
                .contains("< DATE '2026-10-07' THEN 'EXPIRED'")
                .contains("<= DATE '2026-11-06' THEN 'EXPIRING'")
                .contains("ELSE 'VALID'");
    }

    @Test
    void lastActivityIsTheLaterOfTheTwoAndHandlesMissingValues() {
        Instant early = Instant.parse("2026-10-07T04:00:00Z"), late = Instant.parse("2026-10-07T09:30:00Z");
        assertThat(GuardDirectoryService.latest(early, late)).isEqualTo(late);
        assertThat(GuardDirectoryService.latest(late, early)).isEqualTo(late);
        assertThat(GuardDirectoryService.latest(null, early)).isEqualTo(early);
        assertThat(GuardDirectoryService.latest(early, null)).isEqualTo(early);
        assertThat(GuardDirectoryService.latest(null, null)).isNull();
    }
}

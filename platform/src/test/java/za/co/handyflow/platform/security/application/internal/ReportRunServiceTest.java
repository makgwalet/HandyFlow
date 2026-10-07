package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.dto.ReportCatalogueDtos.Card;
import za.co.handyflow.platform.security.dto.ReportCatalogueDtos.Run;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReportRunServiceTest {

    private static Run run(String key, String by) {
        return new Run(key, "2026-09", null, "PDF", by, Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    void everyReportAppearsOnceInOrderEvenWhenNeverGenerated() {
        List<Card> cards = ReportRunService.cards(List.of());
        assertThat(cards).extracting(Card::key)
                .containsExactly("monthly-summary", "site-coverage", "guard-attendance", "site-access");
        assertThat(cards).allMatch(c -> c.lastRun() == null);
    }

    @Test
    void latestRunIsJoinedOntoItsOwnReportOnly() {
        List<Card> cards = ReportRunService.cards(List.of(run("site-coverage", "Thabo")));
        assertThat(cards.get(1).lastRun().generatedBy()).isEqualTo("Thabo");
        assertThat(cards.get(0).lastRun()).isNull();
        assertThat(cards.get(3).lastRun()).isNull();
    }

    @Test
    void scopesTellTheScreenWhatToAskFor() {
        assertThat(ReportRunService.cards(List.of())).extracting(Card::scope)
                .containsExactly("NONE", "SITE", "GUARD", "SITE");
    }

    @Test
    void aLongSubjectIsClippedToTheColumnWidth() {
        assertThat(ReportRunService.clip("x".repeat(500))).hasSize(ReportRunService.SUBJECT_MAX);
        assertThat(ReportRunService.clip(null)).isNull();
        assertThat(ReportRunService.clip("Centurion Mall")).isEqualTo("Centurion Mall");
    }

    @Test
    void aFailureWritingHistoryDoesNotFailTheReport() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new RuntimeException("db down")).when(jdbc).update(anyString(), (Object[]) any());
        ReportRunService service = new ReportRunService(jdbc);
        service.record(TenantId.of(java.util.UUID.randomUUID()), "monthly-summary", "2026-09", null, "VIEW");
        verify(jdbc).update(anyString(), (Object[]) any());
    }

    @Test
    void rowMapsToARun() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("report_key")).thenReturn("guard-attendance");
        when(rs.getString("period")).thenReturn("2026-09");
        when(rs.getString("subject")).thenReturn("Ann Botha");
        when(rs.getString("format")).thenReturn("VIEW");
        when(rs.getString("generated_by_name")).thenReturn("Thabo");
        when(rs.getTimestamp("generated_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-01T08:00:00Z")));
        Run r = ReportRunService.run(rs);
        assertThat(r.subject()).isEqualTo("Ann Botha");
        assertThat(r.generatedAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
    }
}

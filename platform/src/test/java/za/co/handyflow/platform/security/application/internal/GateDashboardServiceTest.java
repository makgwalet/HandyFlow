package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.dto.GateDashboardDtos.OnSiteRow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GateDashboardServiceTest {

    @Test
    void todayBeginsAtMidnightInSouthAfricanTime() {
        // 10:00 SAST on 7 October is 08:00Z; SAST midnight is 22:00Z the evening before.
        assertThat(GateDashboardService.todayStart(Instant.parse("2026-10-07T08:00:00Z"))).isEqualTo(Instant.parse("2026-10-06T22:00:00Z"));
    }

    @Test
    void justAfterMidnightSastIsAlreadyTheNextDayEvenThoughUtcIsStillYesterday() {
        assertThat(GateDashboardService.todayStart(Instant.parse("2026-10-07T22:30:00Z"))).isEqualTo(Instant.parse("2026-10-07T22:00:00Z"));
        assertThat(GateDashboardService.todayStart(Instant.parse("2026-10-07T21:59:00Z"))).isEqualTo(Instant.parse("2026-10-06T22:00:00Z"));
    }

    @Test
    void aRowCarriesNamesAndStatusButNoIdOrPhoneNumbers() throws SQLException {
        UUID id = UUID.randomUUID(), site = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id")).thenReturn(id);
        when(rs.getObject("site_id")).thenReturn(site);
        when(rs.getString("site_name")).thenReturn("Centurion Mall");
        when(rs.getString("access_point_name")).thenReturn("Main gate");
        when(rs.getString("entry_type")).thenReturn("VISITOR");
        when(rs.getString("person_name")).thenReturn("Ann Botha");
        when(rs.getString("company")).thenReturn("Acme");
        when(rs.getString("host_name")).thenReturn("Pieter");
        when(rs.getString("vehicle_registration")).thenReturn("CA 123-456");
        when(rs.getTimestamp("logged_in_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-07T06:00:00Z")));
        when(rs.getString("status")).thenReturn("OVERSTAYED");

        OnSiteRow r = GateDashboardService.row(rs);
        assertThat(r.personName()).isEqualTo("Ann Botha");
        assertThat(r.status()).isEqualTo("OVERSTAYED");
        assertThat(r.loggedInAt()).isEqualTo(Instant.parse("2026-10-07T06:00:00Z"));
        assertThat(OnSiteRow.class.getRecordComponents()).extracting(c -> c.getName()).doesNotContain("idNumber", "phone");
    }
}

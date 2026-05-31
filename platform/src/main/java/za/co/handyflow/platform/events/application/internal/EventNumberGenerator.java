package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class EventNumberGenerator {

    private final JdbcTemplate jdbc;

    // Format: EVT-2026-00001
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE tenant_id = ? AND EXTRACT(YEAR FROM created_at) = ?",
                Integer.class, tenantId.getValue(), year);
        int seq = (count != null ? count : 0) + 1;
        return String.format("EVT-%d-%05d", year, seq);
    }

    // Ticket number: EVT-2026-00001-0042
    public String nextTicket(String eventNumber, long guestSeq) {
        return eventNumber + "-" + String.format("%04d", guestSeq);
    }
}
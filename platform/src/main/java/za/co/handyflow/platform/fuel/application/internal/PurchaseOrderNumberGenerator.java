package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/** Supplier order numbers PO-YYYY-NNNNN, sequential per tenant and year. */
@Component
@RequiredArgsConstructor
public class PurchaseOrderNumberGenerator {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(TenantId tenantId) {
        int year = LocalDate.now().getYear();
        jdbc.update("INSERT INTO fuel_po_sequences (tenant_id, year, last_seq) VALUES (?, ?, 0) ON CONFLICT (tenant_id, year) DO NOTHING",
                tenantId.getValue(), year);
        Integer seq = jdbc.queryForObject("UPDATE fuel_po_sequences SET last_seq = last_seq + 1 WHERE tenant_id = ? AND year = ? RETURNING last_seq",
                Integer.class, tenantId.getValue(), year);
        return String.format("PO-%d-%05d", year, seq != null ? seq : 1);
    }
}

package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;

/** The company's name, telephone, email and address as kept in the company settings, for the e@syFile import file. Anything missing is null. */
@Component
@RequiredArgsConstructor
public class EmployerDetails {

    public record Company(String name, String phone, String email, Map<String, String> address) {}

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final JdbcTemplate jdbc;

    public Company of(TenantId tenantId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT name, phone, email, address::text AS address FROM tenants WHERE id = ?", tenantId.getValue());
            Map<String, String> address = null;
            Object raw = row.get("address");
            if (raw != null && !raw.toString().isBlank())
                address = JSON.readValue(raw.toString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            return new Company(text(row.get("name")), text(row.get("phone")), text(row.get("email")), address);
        } catch (Exception e) {
            return new Company(null, null, null, null);
        }
    }

    private static String text(Object o) { return o == null ? null : o.toString(); }
}

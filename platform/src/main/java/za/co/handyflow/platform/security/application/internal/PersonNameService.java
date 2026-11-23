package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Names of the company's own users, for screens that show who did something. A person who cannot be found is simply absent from the answer. */
@Service
@RequiredArgsConstructor
public class PersonNameService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<UUID, String> names(TenantId tenantId, Collection<UUID> ids) {
        List<UUID> wanted = PersonNameRules.distinct(ids);
        if (wanted.isEmpty()) return Map.of();
        String marks = String.join(", ", java.util.Collections.nCopies(wanted.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId.getValue());
        args.addAll(wanted);
        Map<UUID, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT id, first_name, last_name, email FROM users WHERE tenant_id = ? AND id IN (" + marks + ")", (rs, n) -> {
            String name = PersonNameRules.display(rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"));
            if (name != null) out.put(rs.getObject("id", UUID.class), name);
            return name;
        }, args.toArray());
        return out;
    }
}

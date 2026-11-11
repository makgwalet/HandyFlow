package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules.IndustryProfile;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules.ItemDefaults;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules.OperatingModel;

import java.util.List;
import java.util.UUID;

/** Reads and changes the operating model and industry profile, and applies them where they matter: new client accounts and new items. */
@Service
@RequiredArgsConstructor
public class WhseSettingsService {

    private final JdbcTemplate jdbc;

    public record Settings(boolean profileExists, OperatingModel operatingModel, IndustryProfile industryProfile, ItemDefaults itemDefaults, int activeClients) {}

    /** Without a profile row the defaults apply (3PL, general), which is how the module behaved before these settings existed. */
    @Transactional(readOnly = true)
    public Settings get(TenantId tenantId) {
        List<String[]> r = jdbc.query("SELECT operating_model, industry_profile FROM whse_profile WHERE tenant_id = ?", (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, tenantId.getValue());
        OperatingModel m = r.isEmpty() ? OperatingModel.THREE_PL : WhseSettingsRules.parseModel(r.get(0)[0]);
        IndustryProfile p = r.isEmpty() ? IndustryProfile.GENERAL : WhseSettingsRules.parseIndustry(r.get(0)[1]);
        return new Settings(!r.isEmpty(), m, p, WhseSettingsRules.itemDefaults(p), activeClients(tenantId));
    }

    @Transactional
    public Settings update(TenantId tenantId, String operatingModel, String industryProfile, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        OperatingModel m = WhseSettingsRules.parseModel(operatingModel);
        IndustryProfile p = WhseSettingsRules.parseIndustry(industryProfile);
        Settings before = get(tenantId);
        if (!before.profileExists()) throw new IllegalStateException("Set up the warehouse profile first.");
        WhseSettingsRules.checkModelChange(m, before.activeClients());
        jdbc.update("UPDATE whse_profile SET operating_model = ?, industry_profile = ?, updated_at = now() WHERE tenant_id = ?", m.name(), p.name(), tenantId.getValue());
        if (m != before.operatingModel() || p != before.industryProfile()) {
            jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, 'WHSE_SETTINGS_CHANGED', 'WHSE_PROFILE', jsonb_build_object('operatingModel', ?::text, 'industryProfile', ?::text, 'wasOperatingModel', ?::text, 'wasIndustryProfile', ?::text))",
                    tenantId.getValue(), actor, m.name(), p.name(), before.operatingModel().name(), before.industryProfile().name());
        }
        return get(tenantId);
    }

    /** Refuses a new client account the operating model does not allow. */
    @Transactional(readOnly = true)
    public void requireCanAddClient(TenantId tenantId) {
        Settings s = get(tenantId);
        WhseSettingsRules.checkNewClient(s.operatingModel(), s.activeClients());
    }

    /** The tracking a new item starts with. */
    @Transactional(readOnly = true)
    public ItemDefaults itemDefaultsFor(TenantId tenantId) {
        List<String> r = jdbc.query("SELECT industry_profile FROM whse_profile WHERE tenant_id = ?", (rs, i) -> rs.getString(1), tenantId.getValue());
        return r == null || r.isEmpty() || r.get(0) == null ? new ItemDefaults(false, false) : WhseSettingsRules.itemDefaults(WhseSettingsRules.parseIndustry(r.get(0)));
    }

    private int activeClients(TenantId tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_clients WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class, tenantId.getValue());
        return n == null ? 0 : n;
    }
}

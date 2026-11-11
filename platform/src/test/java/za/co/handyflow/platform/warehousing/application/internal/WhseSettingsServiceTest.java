package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseSettingsServiceTest {

    private JdbcTemplate jdbc;
    private WhseSettingsService service;
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseSettingsService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    private void profile(String model, String industry, int clients) {
        when(jdbc.query(contains("operating_model, industry_profile"), any(RowMapper.class), any(Object[].class))).thenReturn(List.<String[]>of(new String[]{model, industry}));
        when(jdbc.queryForObject(contains("FROM whse_clients"), eq(Integer.class), any(Object[].class))).thenReturn(clients);
    }

    @Test @DisplayName("without a profile the module behaves as before: 3PL, general, nothing preset")
    void defaults() {
        when(jdbc.query(contains("operating_model, industry_profile"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("FROM whse_clients"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        WhseSettingsService.Settings s = service.get(t());
        assertFalse(s.profileExists());
        assertEquals(WhseSettingsRules.OperatingModel.THREE_PL, s.operatingModel());
        assertFalse(s.itemDefaults().lotTracked());
    }

    @Test @DisplayName("a second client account is refused for an internal warehouse and allowed for hybrid")
    void clientGate() {
        profile("INTERNAL", "GENERAL", 1);
        assertThrows(IllegalStateException.class, () -> service.requireCanAddClient(t()));
        profile("HYBRID", "GENERAL", 1);
        assertDoesNotThrow(() -> service.requireCanAddClient(t()));
    }

    @Test @DisplayName("changing to internal with several accounts open is refused and writes nothing")
    void refusesInternalWithManyClients() {
        profile("THREE_PL", "GENERAL", 3);
        assertThrows(IllegalStateException.class, () -> service.update(t(), "INTERNAL", "GENERAL", actor));
        verify(jdbc, never()).update(contains("UPDATE whse_profile"), any(Object[].class));
    }

    @Test @DisplayName("a change is saved and audited with what it was before")
    void savesAndAudits() {
        profile("THREE_PL", "GENERAL", 1);
        service.update(t(), "hybrid", "pharma", actor);
        verify(jdbc).update(contains("UPDATE whse_profile SET operating_model"), eq("HYBRID"), eq("PHARMA"), eq(tenant));
        verify(jdbc).update(contains("WHSE_SETTINGS_CHANGED"), eq(tenant), eq(actor), eq("HYBRID"), eq("PHARMA"), eq("THREE_PL"), eq("GENERAL"));
    }

    @Test @DisplayName("saving the same values writes no audit event")
    void noAuditWhenUnchanged() {
        profile("HYBRID", "FOOD", 2);
        service.update(t(), "HYBRID", "FOOD", actor);
        verify(jdbc, never()).update(contains("WHSE_SETTINGS_CHANGED"), any(Object[].class));
    }

    @Test @DisplayName("the warehouse profile must exist, and the user must be identified")
    void preconditions() {
        when(jdbc.query(contains("operating_model, industry_profile"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("FROM whse_clients"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.update(t(), "HYBRID", "FOOD", actor));
        assertThrows(AccessDeniedException.class, () -> service.update(t(), "HYBRID", "FOOD", null));
    }

    @Test @DisplayName("item defaults follow the profile")
    void itemDefaults() {
        when(jdbc.query(eq("SELECT industry_profile FROM whse_profile WHERE tenant_id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("FOOD"));
        assertTrue(service.itemDefaultsFor(t()).expiryTracked());
    }
}

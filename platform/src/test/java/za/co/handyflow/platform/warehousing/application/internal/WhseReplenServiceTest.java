package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseReplenServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID face = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseReplenService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseReplenService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void location(String type) {
        when(jdbc.query(contains("SELECT location_type FROM whse_locations"), any(RowMapper.class), any(Object[].class))).thenReturn(type == null ? List.of() : List.of(type));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void noExistingRule() {
        when(jdbc.query(contains("SELECT id FROM whse_replen_rules"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    @Test @DisplayName("a rule is created and audited")
    void creates() {
        when(jdbc.queryForObject(contains("FROM whse_items"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        location("BIN");
        noExistingRule();
        UUID id = service.saveRule(t(), client, item, face, bd("10"), bd("40"), true, actor);
        verify(jdbc).update(contains("INSERT INTO whse_replen_rules"), eq(id), eq(tenant), eq(client), eq(item), eq(face), eq(bd("10")), eq(bd("40")), eq(true), eq(actor));
        verify(jdbc).update(contains("INSERT INTO audit_events"), eq(tenant), eq(actor), eq("WHSE_REPLEN_RULE_SAVED"), anyString());
    }

    @Test @DisplayName("an existing rule has its levels changed instead")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updates() {
        UUID existing = UUID.randomUUID();
        when(jdbc.queryForObject(contains("FROM whse_items"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        location("SHELF");
        when(jdbc.query(contains("SELECT id FROM whse_replen_rules"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(existing));
        assertEquals(existing, service.saveRule(t(), client, item, face, bd("5"), bd("25"), false, actor));
        verify(jdbc).update(contains("UPDATE whse_replen_rules"), eq(bd("5")), eq(bd("25")), eq(false), eq(existing));
    }

    @Test @DisplayName("a dock cannot be a pick face, and unknown items and locations are refused")
    void refusals() {
        when(jdbc.queryForObject(contains("FROM whse_items"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        location("DOCK");
        assertThrows(IllegalArgumentException.class, () -> service.saveRule(t(), client, item, face, bd("1"), bd("2"), true, actor));
        location(null);
        assertThrows(ResourceNotFoundException.class, () -> service.saveRule(t(), client, item, face, bd("1"), bd("2"), true, actor));
        when(jdbc.queryForObject(contains("FROM whse_items"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(ResourceNotFoundException.class, () -> service.saveRule(t(), client, item, face, bd("1"), bd("2"), true, actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_replen_rules"), any(Object[].class));
    }

    @Test @DisplayName("levels and the user are checked before anything is read")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.saveRule(t(), client, item, face, bd("10"), bd("10"), true, actor));
        assertThrows(AccessDeniedException.class, () -> service.saveRule(t(), client, item, face, bd("1"), bd("2"), true, null));
        assertThrows(AccessDeniedException.class, () -> service.deleteRule(t(), UUID.randomUUID(), null));
    }

    @Test @DisplayName("deleting an unknown rule is a not-found")
    void deleteUnknown() {
        when(jdbc.update(contains("DELETE FROM whse_replen_rules"), any(Object[].class))).thenReturn(0);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteRule(t(), UUID.randomUUID(), actor));
    }

    private static final String SRC_MARK = "FROM whse_inventory v JOIN whse_locations loc";

    /** The rules query returns one row, built by running the real row mapper over a stubbed ResultSet. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void oneRule(String min, String max, String faceQty, String inbound, String reserve, boolean active) throws Exception {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getObject(eq(1), eq(UUID.class))).thenReturn(UUID.randomUUID());
        when(rs.getObject(eq(2), eq(UUID.class))).thenReturn(client);
        when(rs.getObject(eq(4), eq(UUID.class))).thenReturn(item);
        when(rs.getObject(eq(7), eq(UUID.class))).thenReturn(face);
        when(rs.getString(anyInt())).thenReturn("x");
        when(rs.getBigDecimal(9)).thenReturn(bd(min));
        when(rs.getBigDecimal(10)).thenReturn(bd(max));
        when(rs.getBoolean(11)).thenReturn(active);
        when(rs.getBigDecimal(12)).thenReturn(bd(faceQty));
        when(rs.getBigDecimal(13)).thenReturn(bd(inbound));
        when(rs.getBigDecimal(14)).thenReturn(bd(reserve));
        when(jdbc.query(contains("FROM whse_replen_rules r"), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));
    }

    @Test @DisplayName("sync creates a task per reserve position that is needed")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void syncCreatesTasks() throws Exception {
        oneRule("10", "30", "2", "0", "100", true);
        UUID reserveLoc = UUID.randomUUID();
        when(jdbc.query(contains(SRC_MARK), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseReplenRules.Source(reserveLoc, null, null, 9, bd("100"))));
        when(jdbc.update(contains("INSERT INTO whse_tasks"), any(Object[].class))).thenReturn(1);
        assertEquals(1, service.sync(t()));
        verify(jdbc).update(contains("INSERT INTO whse_tasks"), any(UUID.class), eq(tenant), eq(client), eq(item), isNull(), eq(reserveLoc), eq(face), eq(bd("28")), eq(reserveLoc));
    }

    @Test @DisplayName("sync leaves a face that is fine, one that is off, and one already being filled")
    void syncSkips() throws Exception {
        oneRule("10", "30", "12", "0", "100", true);
        assertEquals(0, service.sync(t()));
        oneRule("10", "30", "2", "0", "100", false);
        assertEquals(0, service.sync(t()));
        oneRule("10", "30", "2", "28", "100", true);
        assertEquals(0, service.sync(t()));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tasks"), any(Object[].class));
    }

    @Test @DisplayName("the rule list reports the status of each face")
    void statuses() throws Exception {
        oneRule("10", "30", "2", "0", "0", true);
        assertEquals("NO_RESERVE", service.rules(t()).get(0).status());
        oneRule("10", "30", "2", "0", "50", true);
        assertEquals("BELOW_MIN", service.rules(t()).get(0).status());
        oneRule("10", "30", "20", "0", "50", false);
        assertEquals("OFF", service.rules(t()).get(0).status());
    }
}

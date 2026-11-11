package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhsePackingServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID pkg = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhsePackingService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhsePackingService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void orderStatus(String status) {
        when(jdbc.query(contains("FROM whse_outbound_orders WHERE tenant_id = ? AND id = ? FOR UPDATE"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(status));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void lockedPackage(String status, String tare) {
        when(jdbc.query(contains("FROM whse_packages WHERE tenant_id = ? AND id = ? FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{order, 1, status, new BigDecimal(tare)}));
    }

    @Test @DisplayName("a package is numbered after the last one and audited")
    void creates() {
        orderStatus("PICKING");
        when(jdbc.queryForObject(contains("max(package_no)"), eq(Integer.class), any(Object[].class))).thenReturn(2);
        UUID id = service.createPackage(t(), order, "pallet", 1200, 1000, null, new BigDecimal("20"), actor);
        assertNotNull(id);
        verify(jdbc).update(contains("INSERT INTO whse_packages"), eq(id), eq(tenant), eq(order), eq(3), eq("PALLET"), eq(1200), eq(1000), isNull(), eq(new BigDecimal("20")), eq(actor));
        verify(jdbc).update(contains("INSERT INTO audit_events"), eq(tenant), eq(actor), eq("WHSE_PACKAGE_CREATED"), anyString());
    }

    @Test @DisplayName("packages are only made while the order is being picked")
    void notWhenPacked() {
        orderStatus("PACKED");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.createPackage(t(), order, "BOX", null, null, null, null, actor));
        assertTrue(e.getMessage().contains("being picked"));
        verify(jdbc, never()).update(contains("INSERT INTO whse_packages"), any(Object[].class));
    }

    @Test @DisplayName("an unidentified user cannot pack")
    void needsActor() {
        assertThrows(AccessDeniedException.class, () -> service.createPackage(t(), order, "BOX", null, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.closePackage(t(), pkg, BigDecimal.ONE, null));
    }

    @Test @DisplayName("a closed package cannot be changed or deleted")
    void closedIsLocked() {
        lockedPackage("CLOSED", "0");
        assertThrows(HandyFlowException.class, () -> service.setItem(t(), pkg, UUID.randomUUID(), BigDecimal.ONE, actor));
        assertThrows(HandyFlowException.class, () -> service.deletePackage(t(), pkg, actor));
        assertThrows(HandyFlowException.class, () -> service.closePackage(t(), pkg, BigDecimal.TEN, actor));
    }

    @Test @DisplayName("an empty package cannot be closed")
    void emptyClose() {
        lockedPackage("OPEN", "0");
        when(jdbc.queryForObject(contains("count(*) FROM whse_package_items"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.closePackage(t(), pkg, BigDecimal.TEN, actor));
        verify(jdbc, never()).update(contains("status = 'CLOSED'"), any(Object[].class));
    }

    @Test @DisplayName("closing records the weight and audits it")
    void closes() {
        lockedPackage("OPEN", "0.5");
        when(jdbc.queryForObject(contains("count(*) FROM whse_package_items"), eq(Integer.class), any(Object[].class))).thenReturn(2);
        service.closePackage(t(), pkg, new BigDecimal("7.5"), actor);
        verify(jdbc).update(contains("status = 'CLOSED'"), eq(new BigDecimal("7.5")), eq(pkg));
        verify(jdbc).update(contains("INSERT INTO audit_events"), eq(tenant), eq(actor), eq("WHSE_PACKAGE_CLOSED"), anyString());
    }

    @Test @DisplayName("a gross weight not above the empty weight is refused")
    void grossAboveTare() {
        lockedPackage("OPEN", "2");
        when(jdbc.queryForObject(contains("count(*) FROM whse_package_items"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.closePackage(t(), pkg, new BigDecimal("2"), actor));
    }

    @Test @DisplayName("a label needs a closed package")
    void labelNeedsClosed() {
        lockedPackage("OPEN", "0");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.label(t(), pkg));
        assertTrue(e.getMessage().contains("Close the package"));
    }

    @Test @DisplayName("an open package is reopened only while the order is being picked")
    void reopenGuard() {
        lockedPackage("CLOSED", "0");
        when(jdbc.queryForObject(contains("SELECT status FROM whse_outbound_orders"), eq(String.class), any(Object[].class))).thenReturn("PACKED");
        assertThrows(HandyFlowException.class, () -> service.reopenPackage(t(), pkg, actor));
        when(jdbc.queryForObject(contains("SELECT status FROM whse_outbound_orders"), eq(String.class), any(Object[].class))).thenReturn("PICKING");
        service.reopenPackage(t(), pkg, actor);
        verify(jdbc).update(contains("status = 'OPEN'"), eq(pkg));
    }
}

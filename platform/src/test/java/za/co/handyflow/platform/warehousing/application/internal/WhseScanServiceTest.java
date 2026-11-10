package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseScanServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private WhseLocationRepository repo;
    private WhseScanService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repo = mock(WhseLocationRepository.class);
        service = new WhseScanService(jdbc, repo);
        when(repo.save(any(WhseLocation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void returns(String fragment, Object... rows) {
        when(jdbc.query(contains(fragment), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("a scan nobody could have meant is refused before any query runs")
    void emptyAndTooLong() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), "  "));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), null));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), "x".repeat(101)));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("every kind of thing is searched with the trimmed code, and all matches come back in a fixed order")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resolvesAcrossTypes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        returns("FROM whse_locations", new WhseScanService.Match("LOCATION", a, "A-01-01", null, null));
        returns("FROM whse_items", new WhseScanService.Match("ITEM", b, "SKU1", "Widget · Acme", UUID.randomUUID()),
                new WhseScanService.Match("ITEM", UUID.randomUUID(), "SKU1", "Widget · Karoo", UUID.randomUUID()));
        List<WhseScanService.Match> out = service.resolve(t(), "  A-01-01 ");
        assertEquals(List.of("LOCATION", "ITEM", "ITEM"), out.stream().map(WhseScanService.Match::type).toList());
        verify(jdbc, times(6)).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(jdbc).query(contains("FROM whse_locations"), any(RowMapper.class), eq(tenant), eq("A-01-01"), eq("A-01-01"));
    }

    @Test
    @DisplayName("nothing matching is an empty list, not an error")
    void noMatch() {
        assertTrue(service.resolve(t(), "ZZZ").isEmpty());
    }

    @Test
    @DisplayName("a location gets its own barcode, and a blank clears it")
    void setAndClear() {
        UUID id = UUID.randomUUID();
        WhseLocation l = WhseLocation.create(tenant, "A-01-01", null, null, null);
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(l));
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        service.setLocationBarcode(t(), id, " LOC9 ");
        assertEquals("LOC9", l.getBarcode());
        service.setLocationBarcode(t(), id, "  ");
        assertNull(l.getBarcode());
    }

    @Test
    @DisplayName("a barcode that is another location's barcode or code is refused, so a scan never means two places")
    void clash() {
        UUID id = UUID.randomUUID();
        WhseLocation l = WhseLocation.create(tenant, "A-01-01", null, null, null);
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(l));
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.setLocationBarcode(t(), id, "B-02-02"));
        assertEquals("BARCODE_IN_USE", e.getErrorCode());
        assertNull(l.getBarcode());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("spaces are refused and an unknown location is not found")
    void validation() {
        UUID id = UUID.randomUUID();
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(WhseLocation.create(tenant, "A-01-01", null, null, null)));
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.setLocationBarcode(t(), id, "has space"));
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.setLocationBarcode(t(), id, "X1"));
    }
}

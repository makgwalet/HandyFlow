package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseFloorMapServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private WhseLocationRepository repo;
    private WhseFloorMapService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repo = mock(WhseLocationRepository.class);
        service = new WhseFloorMapService(jdbc, repo);
        when(repo.save(any(WhseLocation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private TenantId t() { return TenantId.of(tenant); }
    private WhseFloorLayout.Spec spec() { return new WhseFloorLayout.Spec("Z", List.of("A"), 2, 1, "BIN", 0, 0, 2); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void existing(List<String> codes, List<String> cells) {
        when(jdbc.queryForObject(contains("max(pick_sequence)"), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForList(contains("SELECT code"), eq(String.class), any(Object[].class))).thenReturn(codes);
        when(jdbc.query(contains("SELECT pos_x, pos_y"), any(RowMapper.class), any(Object[].class))).thenReturn(cells);
    }

    @Test @DisplayName("generate creates every location with its cell and picking order")
    void generates() {
        existing(List.of(), List.of());
        assertEquals(2, service.generate(t(), spec(), null, actor));
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        WhseLocation first = c.getAllValues().get(0);
        assertEquals("A-01-01", first.getCode());
        assertEquals(0, first.getPosX());
        assertEquals(1, first.getPickSequence());
        assertEquals("BIN", first.getLocationType());
        verify(jdbc).update(contains("WHSE_LOCATIONS_GENERATED"), any(Object[].class));
    }

    @Test @DisplayName("picking order continues after the highest existing one")
    void continuesSequence() {
        existing(List.of(), List.of());
        when(jdbc.queryForObject(contains("max(pick_sequence)"), eq(Integer.class), any(Object[].class))).thenReturn(40);
        service.generate(t(), spec(), null, actor);
        ArgumentCaptor<WhseLocation> c = ArgumentCaptor.forClass(WhseLocation.class);
        verify(repo, times(2)).save(c.capture());
        assertEquals(41, c.getAllValues().get(0).getPickSequence());
    }

    @Test @DisplayName("a clashing code creates nothing")
    void codeClash() {
        existing(List.of("A-02-01"), List.of());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.generate(t(), spec(), null, actor));
        assertEquals("FLOOR_LAYOUT_CONFLICT", e.getErrorCode());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("an occupied cell creates nothing")
    void cellClash() {
        existing(List.of(), List.of("1:0:1"));
        assertThrows(HandyFlowException.class, () -> service.generate(t(), spec(), null, actor));
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("preview flags conflicts without saving")
    void previewFlags() {
        existing(List.of("A-01-01"), List.of());
        List<WhseFloorMapService.PreviewSlot> p = service.preview(t(), spec());
        assertNotNull(p.get(0).conflict());
        assertNull(p.get(1).conflict());
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("moving a location to an occupied cell is refused")
    void moveClash() {
        UUID id = UUID.randomUUID();
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(WhseLocation.create(tenant, "X", null, null, null)));
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.setLayout(t(), id, "A", 1, 1, "BIN", 1, 3, 4));
        assertEquals("CELL_OCCUPIED", e.getErrorCode());
    }

    @Test @DisplayName("a free cell is accepted, and no position takes the location off the map")
    void moveAndRemove() {
        UUID id = UUID.randomUUID();
        WhseLocation l = WhseLocation.create(tenant, "X", null, null, null);
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(l));
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        service.setLayout(t(), id, "a", 1, 1, "SHELF", 5, 3, 4);
        assertEquals(3, l.getPosX());
        assertEquals("A", l.getAisle());
        service.setLayout(t(), id, "A", 1, 1, "SHELF", 5, null, null);
        assertNull(l.getPosX());
        assertNull(l.getPosY());
    }

    @Test @DisplayName("half a position is refused, and an unknown location is not found")
    void validation() {
        UUID id = UUID.randomUUID();
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.of(WhseLocation.create(tenant, "X", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.setLayout(t(), id, null, null, null, "BIN", null, 3, null));
        when(repo.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.setLayout(t(), id, null, null, null, "BIN", null, null, null));
    }

    @Test @DisplayName("utilisation is stock over capacity, empty when there is no capacity")
    void utilisation() {
        assertEquals(new BigDecimal("62.5"), WhseFloorMapService.utilisation(new BigDecimal("25"), new BigDecimal("40")));
        assertNull(WhseFloorMapService.utilisation(new BigDecimal("25"), null));
        assertNull(WhseFloorMapService.utilisation(new BigDecimal("25"), BigDecimal.ZERO));
    }
}
